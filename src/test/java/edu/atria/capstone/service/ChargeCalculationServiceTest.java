package edu.atria.capstone.service;

import edu.atria.capstone.model.ChargesBreakdown;
import edu.atria.capstone.model.ProductType;
import edu.atria.capstone.model.TransactionSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Statutory & Brokerage Charges Engine Tests")
class ChargeCalculationServiceTest {

    private ChargeCalculationService chargeService;

    @BeforeEach
    void setUp() {
        chargeService = new ChargeCalculationService();
    }

    @Test
    @DisplayName("Delivery brokerage must strictly be Rs 0.00")
    void testDeliveryBrokerageIsZero() {
        BigDecimal tradeValue = new BigDecimal("100000.00");
        BigDecimal brokerage = chargeService.calculateDeliveryBrokerage(tradeValue);
        assertEquals(new BigDecimal("0.00"), brokerage);
    }

    @Test
    @DisplayName("Intraday brokerage applies minimum Rs 1.00 when 0.03% is lower than Rs 1")
    void testIntradayBrokerageMinimumFloor() {
        // Trade value Rs 2000: 0.03% = Rs 0.60 -> must floor to Rs 1.00
        BigDecimal tradeValue = new BigDecimal("2000.00");
        BigDecimal brokerage = chargeService.calculateIntradayBrokerage(tradeValue);
        assertEquals(new BigDecimal("1.00"), brokerage);
    }

    @Test
    @DisplayName("Intraday brokerage applies 0.03% proportionally between Rs 1 and Rs 20")
    void testIntradayBrokerageProportional() {
        // Trade value Rs 50,000: 0.03% = Rs 15.00
        BigDecimal tradeValue = new BigDecimal("50000.00");
        BigDecimal brokerage = chargeService.calculateIntradayBrokerage(tradeValue);
        assertEquals(new BigDecimal("15.00"), brokerage);
    }

    @Test
    @DisplayName("Intraday brokerage caps at maximum Rs 20.00 per executed order")
    void testIntradayBrokerageMaximumCap() {
        // Trade value Rs 10,00,000: 0.03% = Rs 300.00 -> must cap at Rs 20.00
        BigDecimal tradeValue = new BigDecimal("1000000.00");
        BigDecimal brokerage = chargeService.calculateIntradayBrokerage(tradeValue);
        assertEquals(new BigDecimal("20.00"), brokerage);
    }

    @Test
    @DisplayName("Delivery STT is 0.1% on both Buy and Sell")
    void testDeliverySTT() {
        BigDecimal tradeValue = new BigDecimal("100000.00");
        BigDecimal buyStt = chargeService.calculateDeliverySTT(TransactionSide.BUY, tradeValue);
        BigDecimal sellStt = chargeService.calculateDeliverySTT(TransactionSide.SELL, tradeValue);

        assertEquals(new BigDecimal("100.00"), buyStt);
        assertEquals(new BigDecimal("100.00"), sellStt);
    }

    @Test
    @DisplayName("Intraday STT is 0.025% on Sell only, and Rs 0.00 on Buy")
    void testIntradaySTT() {
        BigDecimal tradeValue = new BigDecimal("100000.00");
        BigDecimal buyStt = chargeService.calculateIntradaySTT(TransactionSide.BUY, tradeValue);
        BigDecimal sellStt = chargeService.calculateIntradaySTT(TransactionSide.SELL, tradeValue);

        assertEquals(new BigDecimal("0.00"), buyStt);
        assertEquals(new BigDecimal("25.00"), sellStt);
    }

    @Test
    @DisplayName("Exchange transaction charge is 0.00345% of trade value")
    void testExchangeTransactionCharge() {
        BigDecimal tradeValue = new BigDecimal("1000000.00");
        BigDecimal exchangeCharge = chargeService.calculateExchangeTransactionCharge(tradeValue);
        assertEquals(new BigDecimal("34.50"), exchangeCharge);
    }

    @Test
    @DisplayName("SEBI turnover charge is Rs 10 per crore (0.000001 of trade value)")
    void testSebiCharge() {
        BigDecimal tradeValue = new BigDecimal("1000000.00"); // 10 Lakhs = 0.1 Crore -> Rs 1.00
        BigDecimal sebiCharge = chargeService.calculateSebiCharge(tradeValue);
        assertEquals(new BigDecimal("1.00"), sebiCharge);

        BigDecimal oneCrore = new BigDecimal("10000000.00");
        assertEquals(new BigDecimal("10.00"), chargeService.calculateSebiCharge(oneCrore));
    }

    @Test
    @DisplayName("Stamp duty: 0.015% on Delivery Buy, 0.003% on Intraday Buy, 0 on Sell")
    void testStampDuty() {
        BigDecimal tradeValue = new BigDecimal("1000000.00");
        BigDecimal deliveryBuyStamp = chargeService.calculateStampDuty(ProductType.DELIVERY, TransactionSide.BUY, tradeValue);
        BigDecimal intradayBuyStamp = chargeService.calculateStampDuty(ProductType.INTRADAY, TransactionSide.BUY, tradeValue);
        BigDecimal sellStamp = chargeService.calculateStampDuty(ProductType.DELIVERY, TransactionSide.SELL, tradeValue);

        assertEquals(new BigDecimal("150.00"), deliveryBuyStamp);
        assertEquals(new BigDecimal("30.00"), intradayBuyStamp);
        assertEquals(new BigDecimal("0.00"), sellStamp);
    }

    @Test
    @DisplayName("GST is 18% on (Brokerage + Exchange Transaction Charge)")
    void testGST() {
        BigDecimal brokerage = new BigDecimal("20.00");
        BigDecimal exchangeCharge = new BigDecimal("30.00");
        // Base = 50.00 * 18% = 9.00
        BigDecimal gst = chargeService.calculateGST(brokerage, exchangeCharge);
        assertEquals(new BigDecimal("9.00"), gst);
    }

    @Test
    @DisplayName("Verify complete charges breakdown matching benchmark test scenario EC-06")
    void testCompleteChargesBreakdownBenchmark() {
        // From EC-06: 400 KRISHNAIT @ 2500.00 = Rs 10,00,000 DELIVERY BUY
        // STT 1,000.00 + exchange 34.50 + SEBI 1.00 + stamp 150.00 + GST 6.21 = Rs 1,191.71
        BigDecimal tradeValue = new BigDecimal("1000000.00");
        ChargesBreakdown breakdown = chargeService.calculateAllCharges(
                ProductType.DELIVERY, TransactionSide.BUY, tradeValue);

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
