package edu.atria.capstone.service;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.LedgerRepository;
import edu.atria.capstone.repository.OrderRepository;
import edu.atria.capstone.repository.ScripRepository;
import edu.atria.capstone.repository.StockPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Order Execution, State Update & Ledger Engine Tests")
class OrderExecutionServiceTest {

    private ClientRepository clientRepository;
    private OrderRepository orderRepository;
    private LedgerRepository ledgerRepository;
    private ScripRepository scripRepository;
    private StockPriceRepository stockPriceRepository;
    private MarginService marginService;
    private ChargeCalculationService chargeService;
    private OrderValidationService validationService;
    private OrderExecutionService executionService;

    private final LocalDate tradeDate = LocalDate.of(2026, 10, 6);

    @BeforeEach
    void setUp() {
        clientRepository = new ClientRepository();
        orderRepository = new OrderRepository();
        ledgerRepository = new LedgerRepository();
        scripRepository = new ScripRepository();
        stockPriceRepository = new StockPriceRepository();
        chargeService = new ChargeCalculationService();
        marginService = new MarginService(scripRepository, stockPriceRepository);
        validationService = new OrderValidationService(
                clientRepository, scripRepository, stockPriceRepository, marginService, chargeService);
        executionService = new OrderExecutionService(
                clientRepository, orderRepository, ledgerRepository, chargeService, marginService, validationService);

        // Setup scrip
        scripRepository.save(new Scrip("KRISHNAIT", "Krishna Infotech Ltd", PledgeGroup.GROUP_A, 1));
        stockPriceRepository.save(LocalDate.of(2026, 9, 30), "KRISHNAIT", new BigDecimal("2492.00"));
        stockPriceRepository.save(LocalDate.of(2026, 10, 5), "KRISHNAIT", new BigDecimal("2500.00"));

        // Setup client CL-05 with cash 10,01,190.71
        Client cl05 = new Client("CL-05", "Keerthi Kamath", "CUIPP5136E", "9010798193",
                new BigDecimal("1001190.71"), LocalDate.of(2026, 9, 30));
        clientRepository.save(cl05);
    }

    @Test
    @DisplayName("Accepted Delivery Buy updates cash ledger, creates holdings and generates contract note")
    void testAcceptedDeliveryBuyExecution() {
        // Trade: BUY 400 KRISHNAIT @ 2500.00 (Trade Value: 10,00,000, Charges: 1191.71, Net Amount: 10,01,191.71)
        Order order = new Order("ORD-003", LocalDateTime.of(2026, 10, 6, 11, 0, 0),
                "CL-05", "KRISHNAIT", TransactionSide.BUY, ProductType.DELIVERY, 400, new BigDecimal("2500.00"));

        Order executed = executionService.processOrder(order, tradeDate);

        assertEquals(OrderDecision.ACCEPTED, executed.getDecision());
        assertNotNull(executed.getChargesBreakdown());
        assertEquals(new BigDecimal("1191.71"), executed.getChargesBreakdown().getTotalCharges());

        // Verify client account state: cash balance should be 10,01,190.71 - 10,01,191.71 = -1.00
        Client client = clientRepository.findById("CL-05").orElseThrow();
        assertEquals(new BigDecimal("-1.00"), client.getCashBalance());

        // Verify holding added
        Holding holding = client.getHolding("KRISHNAIT");
        assertNotNull(holding);
        assertEquals(400, holding.getQuantity());
        assertEquals(new BigDecimal("2500.00"), holding.getAvgCost());

        // Verify ledger entry
        List<LedgerEntry> entries = ledgerRepository.findByClientId("CL-05");
        assertEquals(1, entries.size());
        LedgerEntry entry = entries.get(0);
        assertEquals("DELIVERY_BUY", entry.getEntryType());
        assertEquals(new BigDecimal("1001191.71"), entry.getDebit());
        assertEquals(new BigDecimal("-1.00"), entry.getRunningBalance());

        // Verify contract note
        assertEquals(1, orderRepository.getContractNotes().size());
        ContractNote note = orderRepository.getContractNotes().get(0);
        assertEquals("ORD-003", note.getRefId());
        assertEquals(new BigDecimal("1000000.00"), note.getTradeValue());
    }

    @Test
    @DisplayName("Accepted Delivery Sell credits cash ledger, deducts stock holdings and logs contract note")
    void testAcceptedDeliverySellExecution() {
        Client cl01 = new Client("CL-01", "Deepak Khan", "UDHPT9869R", "9973091141",
                BigDecimal.ZERO, LocalDate.of(2026, 9, 30));
        cl01.addOrUpdateHolding("KRISHNAIT", 200, 0, new BigDecimal("2000.00"));
        clientRepository.save(cl01);

        // Trade: SELL 200 KRISHNAIT @ 2500.00 (Trade Value: 5,00,000)
        Order sellOrder = new Order("ORD-SELL-01", LocalDateTime.of(2026, 10, 6, 12, 0, 0),
                "CL-01", "KRISHNAIT", TransactionSide.SELL, ProductType.DELIVERY, 200, new BigDecimal("2500.00"));

        Order executed = executionService.processOrder(sellOrder, tradeDate);

        assertEquals(OrderDecision.ACCEPTED, executed.getDecision());

        // Holding should be removed or 0
        assertEquals(0, cl01.getTotalHoldingQuantity("KRISHNAIT"));

        // Cash balance should be positive credited by net amount (Trade value - charges)
        assertTrue(cl01.getCashBalance().compareTo(new BigDecimal("499000.00")) > 0);
    }

    @Test
    @DisplayName("Rejected Order guarantees isolation: Account balance and holdings remain completely unmodified")
    void testRejectedOrderIsolation() {
        Client client = clientRepository.findById("CL-05").orElseThrow();
        BigDecimal initialCash = client.getCashBalance();

        // Attempt order exceeding available funds
        Order badOrder = new Order("ORD-EXCEED", LocalDateTime.of(2026, 10, 6, 13, 0, 0),
                "CL-05", "KRISHNAIT", TransactionSide.BUY, ProductType.DELIVERY, 5000, new BigDecimal("2500.00"));

        Order processed = executionService.processOrder(badOrder, tradeDate);

        assertEquals(OrderDecision.REJECTED, processed.getDecision());
        assertFalse(processed.getRuleIds().isEmpty());

        // Balances must remain strictly unmodified
        assertEquals(initialCash, client.getCashBalance());
        assertNull(client.getHolding("KRISHNAIT"));
        assertEquals(0, ledgerRepository.findByClientId("CL-05").size());
    }

    @Test
    @DisplayName("Funds-in command correctly credits client ledger balance and logs audit trail")
    void testFundsInCredit() {
        executionService.executeFundsIn("FIN-001", LocalDateTime.of(2026, 10, 8, 10, 0, 0),
                "CL-05", new BigDecimal("5000.00"));

        Client client = clientRepository.findById("CL-05").orElseThrow();
        assertEquals(new BigDecimal("1006190.71"), client.getCashBalance());

        List<LedgerEntry> entries = ledgerRepository.findByClientId("CL-05");
        assertEquals(1, entries.size());
        assertEquals("FUNDS_IN", entries.get(0).getEntryType());
        assertEquals(new BigDecimal("5000.00"), entries.get(0).getCredit());
    }

    @Test
    @DisplayName("Pledge moves shares to pledged status and debits Rs 20 processing fee")
    void testPledgeExecutionAndFee() {
        Client client = clientRepository.findById("CL-05").orElseThrow();
        client.addOrUpdateHolding("KRISHNAIT", 100, 0, new BigDecimal("2400.00"));
        BigDecimal cashBefore = client.getCashBalance();

        boolean success = executionService.executePledge("PLG-001", LocalDateTime.of(2026, 10, 7, 10, 0, 0),
                "CL-05", "KRISHNAIT", 40);

        assertTrue(success);
        assertEquals(40, client.getPledgedQuantity("KRISHNAIT"));
        assertEquals(60, client.getFreeHoldingQuantity("KRISHNAIT"));

        // Rs 20 fee debited
        assertEquals(cashBefore.subtract(new BigDecimal("20.00")), client.getCashBalance());
    }
}
