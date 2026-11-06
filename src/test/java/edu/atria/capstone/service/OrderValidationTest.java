package edu.atria.capstone.service;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.HolidayRepository;
import edu.atria.capstone.repository.ScripRepository;
import edu.atria.capstone.repository.StockPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Order Validation Engine Tests (Precedence & Business Rules)")
class OrderValidationTest {

    private ClientRepository clientRepository;
    private ScripRepository scripRepository;
    private StockPriceRepository stockPriceRepository;
    private HolidayRepository holidayRepository;
    private MarginService marginService;
    private ChargeCalculationService chargeService;
    private OrderValidationService validationService;

    private final LocalDate tradeDate = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void setUp() {
        clientRepository = new ClientRepository();
        scripRepository = new ScripRepository();
        stockPriceRepository = new StockPriceRepository();
        holidayRepository = new HolidayRepository();
        chargeService = new ChargeCalculationService();
        marginService = new MarginService(scripRepository, stockPriceRepository);
        validationService = new OrderValidationService(
                clientRepository, scripRepository, stockPriceRepository, holidayRepository, marginService, chargeService, null);

        // Setup scrips
        scripRepository.save(new Scrip("CHAMUNDI", "Chamundi Foods Ltd", PledgeGroup.GROUP_A, 1));
        scripRepository.save(new Scrip("UDUPIPHRM", "Udupi Pharma Ltd", PledgeGroup.GROUP_B, 1));

        stockPriceRepository.save(LocalDate.of(2026, 10, 21), "CHAMUNDI", new BigDecimal("500.00"));
        stockPriceRepository.save(LocalDate.of(2026, 10, 21), "UDUPIPHRM", new BigDecimal("350.00"));

        Client client = new Client("CL-06", "Arjun Naik", "SYPPW2837S", "7984162294", new BigDecimal("200000.00"), LocalDate.of(2026, 9, 30));
        clientRepository.save(client);
    }

    @Test
    @DisplayName("Valid order passes all checks")
    void testValidOrderPasses() {
        Order order = new Order("ORD-001", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("500.00"));
        ValidationResult res = validationService.validateOrder(order, tradeDate);
        assertTrue(res.isValid());
    }

    @Test
    @DisplayName("BR_VAL_001: Rejects unregistered client ID")
    void testUnregisteredClientRejection() {
        Order order = new Order("ORD-002", LocalDateTime.now(), "CL-99", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("500.00"));
        ValidationResult res = validationService.validateOrder(order, tradeDate);
        assertFalse(res.isValid());
        assertTrue(res.getRuleIdsString().contains(OrderValidationService.BR_VAL_CLIENT_EXISTS));
    }

    @Test
    @DisplayName("BR_VAL_002: Rejects invalid stock symbol")
    void testInvalidStockSymbolRejection() {
        Order order = new Order("ORD-003", LocalDateTime.now(), "CL-06", "INVALID_STOCK",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("500.00"));
        ValidationResult res = validationService.validateOrder(order, tradeDate);
        assertFalse(res.isValid());
        assertTrue(res.getRuleIdsString().contains(OrderValidationService.BR_VAL_SYMBOL_EXISTS));
    }

    @Test
    @DisplayName("BR_VAL_003 & BR_VAL_004: Rejects non-positive quantity and price")
    void testNonPositiveQuantityAndPrice() {
        Order badOrder = new Order("ORD-004", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, -5, BigDecimal.ZERO);
        ValidationResult res = validationService.validateOrder(badOrder, tradeDate);
        assertFalse(res.isValid());
        assertTrue(res.getRuleIdsString().contains(OrderValidationService.BR_VAL_POSITIVE_QTY));
        assertTrue(res.getRuleIdsString().contains(OrderValidationService.BR_VAL_POSITIVE_PRICE));
    }

    @Test
    @DisplayName("Order Precedence: Holdings check -> Margin check -> Price band check (reports all failures together)")
    void testOrderPrecedenceAndMultiFailureReporting() {
        // CL-02: 150 held, 100 pledged -> 50 free
        Client cl02 = new Client("CL-02", "Geetha Khan", "XKCPR8447E", "8055761698", new BigDecimal("100000.00"), tradeDate);
        cl02.addOrUpdateHolding("UDUPIPHRM", 150, 100, new BigDecimal("350.00"));
        clientRepository.save(cl02);

        // Attempt Delivery SELL of 60 shares (only 50 free) at Rs 999.00 (outside band [315, 385])
        Order badOrder = new Order("ORD-TRAP-117", LocalDateTime.now(), "CL-02", "UDUPIPHRM",
                TransactionSide.SELL, ProductType.DELIVERY, 60, new BigDecimal("999.00"));

        ValidationResult result = validationService.validateOrder(badOrder, tradeDate);
        assertFalse(result.isValid());

        // Precedence: Holdings check (BR_VAL_006) appears before Price band (BR_VAL_007)
        assertEquals("BR_VAL_006;BR_VAL_007", result.getRuleIdsString());
    }
}
