package edu.atria.capstone.service;

import edu.atria.capstone.model.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Brokerage Calculation Tests")
class BrokerageCalculationTest {

    private ChargeCalculationService chargeService;

    @BeforeEach
    void setUp() {
        chargeService = new ChargeCalculationService();
    }

    @Test
    @DisplayName("Delivery brokerage is strictly zero")
    void testDeliveryBrokerageIsZero() {
        BigDecimal brokerage = chargeService.calculateBrokerage(ProductType.DELIVERY, new BigDecimal("100000.00"));
        assertEquals(new BigDecimal("0.00"), brokerage);
    }

    @Test
    @DisplayName("Intraday brokerage enforces minimum floor of Rs 1.00 per executed order")
    void testIntradayBrokerageFloor() {
        // Trade value Rs 2000: 0.03% = Rs 0.60 -> floored to Rs 1.00
        BigDecimal brokerage = chargeService.calculateBrokerage(ProductType.INTRADAY, new BigDecimal("2000.00"));
        assertEquals(new BigDecimal("1.00"), brokerage);
    }

    @Test
    @DisplayName("Intraday brokerage applies 0.03% proportionally between Rs 1 and Rs 20")
    void testIntradayBrokerageProportional() {
        // Trade value Rs 50,000: 0.03% = Rs 15.00
        BigDecimal brokerage = chargeService.calculateBrokerage(ProductType.INTRADAY, new BigDecimal("50000.00"));
        assertEquals(new BigDecimal("15.00"), brokerage);
    }

    @Test
    @DisplayName("Intraday brokerage caps at maximum Rs 20.00 per executed order")
    void testIntradayBrokerageCap() {
        // Trade value Rs 10,00,000: 0.03% = Rs 300.00 -> capped at Rs 20.00
        BigDecimal brokerage = chargeService.calculateBrokerage(ProductType.INTRADAY, new BigDecimal("1000000.00"));
        assertEquals(new BigDecimal("20.00"), brokerage);
    }
}
