package edu.atria.capstone.service;

import edu.atria.capstone.model.ChargesBreakdown;
import edu.atria.capstone.model.ProductType;
import edu.atria.capstone.model.TransactionSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Statutory Regulatory Charges Tests (SEBI / Stamp / STT / GST)")
class StatutoryChargesTest {

    private ChargeCalculationService chargeService;

    @BeforeEach
    void setUp() {
        chargeService = new ChargeCalculationService();
    }

    @Test
    @DisplayName("Delivery STT: 0.1% on both Buy and Sell")
    void testDeliverySTT() {
        BigDecimal tradeValue = new BigDecimal("100000.00");
        assertEquals(new BigDecimal("100.00"), chargeService.calculateDeliverySTT(TransactionSide.BUY, tradeValue));
        assertEquals(new BigDecimal("100.00"), chargeService.calculateDeliverySTT(TransactionSide.SELL, tradeValue));
    }

    @Test
    @DisplayName("Intraday STT: 0.025% on Sell only; Rs 0.00 on Buy")
    void testIntradaySTT() {
        BigDecimal tradeValue = new BigDecimal("100000.00");
        assertEquals(new BigDecimal("0.00"), chargeService.calculateIntradaySTT(TransactionSide.BUY, tradeValue));
        assertEquals(new BigDecimal("25.00"), chargeService.calculateIntradaySTT(TransactionSide.SELL, tradeValue));
    }

    @Test
    @DisplayName("Exchange transaction charge: 0.00345% of trade value")
    void testExchangeTransactionCharge() {
        BigDecimal tradeValue = new BigDecimal("1000000.00");
        assertEquals(new BigDecimal("34.50"), chargeService.calculateExchangeTransactionCharge(tradeValue));
    }

    @Test
    @DisplayName("SEBI turnover fee: Rs 10 per crore (0.000001 of trade value)")
    void testSebiFee() {
        BigDecimal tradeValue = new BigDecimal("1000000.00"); // 10 Lakhs = Rs 1.00
        assertEquals(new BigDecimal("1.00"), chargeService.calculateSebiCharge(tradeValue));
    }

    @Test
    @DisplayName("Stamp duty: 0.015% on Delivery Buy, 0.003% on Intraday Buy, Rs 0 on Sell")
    void testStampDuty() {
        BigDecimal tradeValue = new BigDecimal("1000000.00");
        assertEquals(new BigDecimal("150.00"), chargeService.calculateStampDuty(ProductType.DELIVERY, TransactionSide.BUY, tradeValue));
        assertEquals(new BigDecimal("30.00"), chargeService.calculateStampDuty(ProductType.INTRADAY, TransactionSide.BUY, tradeValue));
        assertEquals(new BigDecimal("0.00"), chargeService.calculateStampDuty(ProductType.DELIVERY, TransactionSide.SELL, tradeValue));
    }

    @Test
    @DisplayName("GST: 18% on (Brokerage + Exchange Transaction Charge)")
    void testGST() {
        BigDecimal brokerage = new BigDecimal("20.00");
        BigDecimal exchangeCharge = new BigDecimal("30.00");
        // Base = 50.00 * 18% = 9.00
        assertEquals(new BigDecimal("9.00"), chargeService.calculateGST(brokerage, exchangeCharge));
    }

    @Test
    @DisplayName("Benchmark EC-06 complete statutory charges breakdown verification")
    void testBenchmarkChargesBreakdown() {
        // Trade value Rs 10,00,000 DELIVERY BUY
        // STT 1,000.00 + exchange 34.50 + SEBI 1.00 + stamp 150.00 + GST 6.21 = Rs 1,191.71
        BigDecimal tradeValue = new BigDecimal("1000000.00");
        ChargesBreakdown breakdown = chargeService.calculateAllCharges(ProductType.DELIVERY, TransactionSide.BUY, tradeValue);

        assertEquals(new BigDecimal("0.00"), breakdown.getBrokerage());
        assertEquals(new BigDecimal("1000.00"), breakdown.getStt());
        assertEquals(new BigDecimal("34.50"), breakdown.getExchangeCharge());
        assertEquals(new BigDecimal("1.00"), breakdown.getSebiFee());
        assertEquals(new BigDecimal("150.00"), breakdown.getStampDuty());
        assertEquals(new BigDecimal("6.21"), breakdown.getGst());
        assertEquals(new BigDecimal("1191.71"), breakdown.getTotalCharges());
        assertEquals(new BigDecimal("1001191.71"), breakdown.getNetAmount());
    }
}
