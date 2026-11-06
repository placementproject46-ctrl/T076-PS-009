package edu.atria.capstone.service;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.ScripRepository;
import edu.atria.capstone.repository.StockPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Order Validation Engine Tests (All Business Rules)")
class OrderValidationServiceTest {

    private ClientRepository clientRepository;
    private ScripRepository scripRepository;
    private StockPriceRepository stockPriceRepository;
    private MarginService marginService;
    private ChargeCalculationService chargeService;
    private OrderValidationService validationService;

    private final LocalDate tradeDate = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void setUp() {
        clientRepository = new ClientRepository();
        scripRepository = new ScripRepository();
        stockPriceRepository = new StockPriceRepository();
        chargeService = new ChargeCalculationService();
        marginService = new MarginService(scripRepository, stockPriceRepository);
        validationService = new OrderValidationService(
                clientRepository, scripRepository, stockPriceRepository, marginService, chargeService);

        // Setup scrip
        scripRepository.save(new Scrip("CHAMUNDI", "Chamundi Foods Ltd", PledgeGroup.GROUP_A, 1));
        scripRepository.save(new Scrip("UDUPIPHRM", "Udupi Pharma Ltd", PledgeGroup.GROUP_B, 1));
        scripRepository.save(new Scrip("BIDARCHEM", "Bidar Chemicals Ltd", PledgeGroup.GROUP_C, 1));

        // Setup base prices for CHAMUNDI on 2026-10-21 (previous close = 500.00)
        stockPriceRepository.save(LocalDate.of(2026, 10, 21), "CHAMUNDI", new BigDecimal("500.00"));
        stockPriceRepository.save(LocalDate.of(2026, 10, 21), "UDUPIPHRM", new BigDecimal("350.00"));

        // Setup client
        Client client = new Client("CL-06", "Arjun Naik", "SYPPW2837S", "7984162294", new BigDecimal("200000.00"), LocalDate.of(2026, 9, 30));
        clientRepository.save(client);
    }

    @Test
    @DisplayName("Valid order passes all validation rules")
    void testValidOrderPasses() {
        Order order = new Order("ORD-001", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("500.00"));

        ValidationResult result = validationService.validateOrder(order, tradeDate);
        assertTrue(result.isValid());
        assertTrue(result.getFailures().isEmpty());
    }

    @Test
    @DisplayName("BR_VAL_001: Rejects non-existent client ID")
    void testNonExistentClient() {
        Order order = new Order("ORD-002", LocalDateTime.now(), "CL-NON-EXISTENT", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("500.00"));

        ValidationResult result = validationService.validateOrder(order, tradeDate);
        assertFalse(result.isValid());
        assertTrue(result.getRuleIdsString().contains(OrderValidationService.BR_VAL_CLIENT_EXISTS));
    }

    @Test
    @DisplayName("BR_VAL_002: Rejects unrecognized stock symbol")
    void testNonExistentStockSymbol() {
        Order order = new Order("ORD-003", LocalDateTime.now(), "CL-06", "UNKNOWN_SYM",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("500.00"));

        ValidationResult result = validationService.validateOrder(order, tradeDate);
        assertFalse(result.isValid());
        assertTrue(result.getRuleIdsString().contains(OrderValidationService.BR_VAL_SYMBOL_EXISTS));
    }

    @Test
    @DisplayName("BR_VAL_003: Rejects zero or negative quantity")
    void testZeroOrNegativeQuantity() {
        Order orderZero = new Order("ORD-004A", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 0, new BigDecimal("500.00"));
        ValidationResult resZero = validationService.validateOrder(orderZero, tradeDate);
        assertFalse(resZero.isValid());
        assertTrue(resZero.getRuleIdsString().contains(OrderValidationService.BR_VAL_POSITIVE_QTY));

        Order orderNeg = new Order("ORD-004B", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, -5, new BigDecimal("500.00"));
        ValidationResult resNeg = validationService.validateOrder(orderNeg, tradeDate);
        assertFalse(resNeg.isValid());
        assertTrue(resNeg.getRuleIdsString().contains(OrderValidationService.BR_VAL_POSITIVE_QTY));
    }

    @Test
    @DisplayName("BR_VAL_004: Rejects zero or negative price")
    void testZeroOrNegativePrice() {
        Order orderZeroPrice = new Order("ORD-005A", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, BigDecimal.ZERO);
        ValidationResult resZero = validationService.validateOrder(orderZeroPrice, tradeDate);
        assertFalse(resZero.isValid());
        assertTrue(resZero.getRuleIdsString().contains(OrderValidationService.BR_VAL_POSITIVE_PRICE));

        Order orderNegPrice = new Order("ORD-005B", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("-10.00"));
        ValidationResult resNeg = validationService.validateOrder(orderNegPrice, tradeDate);
        assertFalse(resNeg.isValid());
        assertTrue(resNeg.getRuleIdsString().contains(OrderValidationService.BR_VAL_POSITIVE_PRICE));
    }

    @Test
    @DisplayName("BR_VAL_007: Benchmark EC-05 price band validation at exactly +/- 10.00% boundaries")
    void testPriceBandValidationEC05Benchmark() {
        // CHAMUNDI previous close: 500.00
        // Lower band (-10%): 450.00
        // Upper band (+10%): 550.00

        // 1. Exactly +10.00% -> Rs 550.00 (INSIDE, ACCEPT)
        Order ordUpper = new Order("ORD-006A", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("550.00"));
        assertTrue(validationService.validateOrder(ordUpper, tradeDate).isValid());

        // 2. Exactly +10.01% -> Rs 550.05 (OUTSIDE, REJECT BR_VAL_007)
        Order ordAbove = new Order("ORD-006B", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("550.05"));
        ValidationResult resAbove = validationService.validateOrder(ordAbove, tradeDate);
        assertFalse(resAbove.isValid());
        assertTrue(resAbove.getRuleIdsString().contains(OrderValidationService.BR_VAL_PRICE_BAND));

        // 3. Exactly -10.00% -> Rs 450.00 (INSIDE, ACCEPT)
        Order ordLower = new Order("ORD-006C", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("450.00"));
        assertTrue(validationService.validateOrder(ordLower, tradeDate).isValid());

        // 4. Exactly -10.01% -> Rs 449.95 (OUTSIDE, REJECT BR_VAL_007)
        Order ordBelow = new Order("ORD-006D", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("449.95"));
        ValidationResult resBelow = validationService.validateOrder(ordBelow, tradeDate);
        assertFalse(resBelow.isValid());
        assertTrue(resBelow.getRuleIdsString().contains(OrderValidationService.BR_VAL_PRICE_BAND));
    }

    @Test
    @DisplayName("BR_VAL_006: Rejects Delivery Sell when free unpledged holdings are insufficient")
    void testInsufficientFreeHoldingsForDeliverySell() {
        Client cl02 = new Client("CL-02", "Geetha Khan", "XKCPR8447E", "8055761698", new BigDecimal("100000.00"), tradeDate);
        // 150 shares held, 100 pledged -> only 50 free
        cl02.addOrUpdateHolding("UDUPIPHRM", 150, 100, new BigDecimal("350.00"));
        clientRepository.save(cl02);

        // Attempt to SELL 60 shares (requires 60 free, only 50 available)
        Order sellOrder = new Order("ORD-007", LocalDateTime.now(), "CL-02", "UDUPIPHRM",
                TransactionSide.SELL, ProductType.DELIVERY, 60, new BigDecimal("350.00"));

        ValidationResult result = validationService.validateOrder(sellOrder, tradeDate);
        assertFalse(result.isValid());
        assertTrue(result.getRuleIdsString().contains(OrderValidationService.BR_VAL_HOLDINGS_FREE));
    }

    @Test
    @DisplayName("BR_VAL_005: Rejects Delivery Buy when available funds are insufficient")
    void testInsufficientFundsForDeliveryBuy() {
        Client poorClient = new Client("CL-POOR", "Poor Trader", "ABCDE1234F", "9999999999", new BigDecimal("100.00"), tradeDate);
        clientRepository.save(poorClient);

        // Buy 100 shares @ 500 = Rs 50,000 + charges, but only Rs 100 available
        Order buyOrder = new Order("ORD-008", LocalDateTime.now(), "CL-POOR", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 100, new BigDecimal("500.00"));

        ValidationResult result = validationService.validateOrder(buyOrder, tradeDate);
        assertFalse(result.isValid());
        assertTrue(result.getRuleIdsString().contains(OrderValidationService.BR_VAL_AVAILABLE_FUNDS));
    }

    @Test
    @DisplayName("Multi-Failure Evaluation: Reports ALL failing rule IDs together rather than stopping after first failure")
    void testMultiFailureEvaluationSimultaneousReporting() {
        // Trap scenario from commands.txt line 117:
        // CL-02: SELL 60 UDUPIPHRM with only 50 free (150 held, 100 pledged) at price Rs 999.00 (far outside +10% band)
        // Must report BOTH BR_VAL_006 AND BR_VAL_007 simultaneously!
        Client cl02 = new Client("CL-02", "Geetha Khan", "XKCPR8447E", "8055761698", new BigDecimal("100000.00"), tradeDate);
        cl02.addOrUpdateHolding("UDUPIPHRM", 150, 100, new BigDecimal("350.00"));
        clientRepository.save(cl02);

        Order badOrder = new Order("ORD-TRAP-117", LocalDateTime.now(), "CL-02", "UDUPIPHRM",
                TransactionSide.SELL, ProductType.DELIVERY, 60, new BigDecimal("999.00"));

        ValidationResult result = validationService.validateOrder(badOrder, tradeDate);
        assertFalse(result.isValid());

        String ruleIds = result.getRuleIdsString();
        // Check that BOTH rule IDs are reported!
        assertTrue(ruleIds.contains(OrderValidationService.BR_VAL_HOLDINGS_FREE), "Must contain BR_VAL_006");
        assertTrue(ruleIds.contains(OrderValidationService.BR_VAL_PRICE_BAND), "Must contain BR_VAL_007");
        assertEquals("BR_VAL_006;BR_VAL_007", ruleIds);
    }

    @Test
    @DisplayName("BR_VAL_008: Pledging GROUP_C scrip is rejected as not pledgeable")
    void testPledgeGroupCRejection() {
        Client cl04 = new Client("CL-04", "Varsha Jain", "NGJPI0029O", "9534778768", new BigDecimal("60000.00"), tradeDate);
        cl04.addOrUpdateHolding("BIDARCHEM", 500, 0, new BigDecimal("60.00"));
        clientRepository.save(cl04);

        ValidationResult res = validationService.validatePledge("CL-04", "BIDARCHEM", 50);
        assertFalse(res.isValid());
        assertTrue(res.getRuleIdsString().contains(OrderValidationService.BR_VAL_PLEDGE_ELIGIBILITY));
    }
}
