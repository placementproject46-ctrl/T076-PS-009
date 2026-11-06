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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Price Band Circuit Limit Validation Tests (+/- 10% / EC-05)")
class PriceBandValidationTest {

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

        scripRepository.save(new Scrip("CHAMUNDI", "Chamundi Foods Ltd", PledgeGroup.GROUP_A, 1));
        // Previous day close (2026-10-21) = Rs 500.00
        stockPriceRepository.save(LocalDate.of(2026, 10, 21), "CHAMUNDI", new BigDecimal("500.00"));

        Client client = new Client("CL-06", "Arjun Naik", "SYPPW2837S", "7984162294",
                new BigDecimal("200000.00"), LocalDate.of(2026, 9, 30));
        clientRepository.save(client);
    }

    @Test
    @DisplayName("EC-05: Order at exactly +10.00% price band boundary (Rs 550.00) is ACCEPTED")
    void testExactPlusTenPercentBoundaryAccepted() {
        Order order = new Order("ORD-EC05-1", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("550.00"));
        ValidationResult res = validationService.validateOrder(order, tradeDate);
        assertTrue(res.isValid());
    }

    @Test
    @DisplayName("EC-05: Order at +10.01% (Rs 550.05) breaches upper circuit and is REJECTED with BR_VAL_007")
    void testUpperCircuitBreachRejected() {
        Order order = new Order("ORD-EC05-2", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("550.05"));
        ValidationResult res = validationService.validateOrder(order, tradeDate);
        assertFalse(res.isValid());
        assertTrue(res.getRuleIdsString().contains(OrderValidationService.BR_VAL_PRICE_BAND));
    }

    @Test
    @DisplayName("EC-05: Order at exactly -10.00% price band boundary (Rs 450.00) is ACCEPTED")
    void testExactMinusTenPercentBoundaryAccepted() {
        Order order = new Order("ORD-EC05-3", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("450.00"));
        ValidationResult res = validationService.validateOrder(order, tradeDate);
        assertTrue(res.isValid());
    }

    @Test
    @DisplayName("EC-05: Order at -10.01% (Rs 449.95) breaches lower circuit and is REJECTED with BR_VAL_007")
    void testLowerCircuitBreachRejected() {
        Order order = new Order("ORD-EC05-4", LocalDateTime.now(), "CL-06", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("449.95"));
        ValidationResult res = validationService.validateOrder(order, tradeDate);
        assertFalse(res.isValid());
        assertTrue(res.getRuleIdsString().contains(OrderValidationService.BR_VAL_PRICE_BAND));
    }
}
