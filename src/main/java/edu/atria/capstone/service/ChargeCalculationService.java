package edu.atria.capstone.service;

import edu.atria.capstone.model.ChargesBreakdown;
import edu.atria.capstone.model.ProductType;
import edu.atria.capstone.model.TransactionSide;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Statutory & Brokerage Charges Engine for BFSI Capital Markets.
 * All calculations use BigDecimal and are rounded to two decimal places (RoundingMode.HALF_UP).
 */
public class ChargeCalculationService {

    // Rates according to Indian Capital Markets regulatory framework (PS-009 / NSE / BSE / SEBI)
    private static final BigDecimal INTRADAY_BROKERAGE_RATE = new BigDecimal("0.0003"); // 0.03%
    private static final BigDecimal INTRADAY_BROKERAGE_MAX = new BigDecimal("20.00");
    private static final BigDecimal INTRADAY_BROKERAGE_MIN = new BigDecimal("1.00");

    private static final BigDecimal DELIVERY_STT_RATE = new BigDecimal("0.001"); // 0.1%
    private static final BigDecimal INTRADAY_SELL_STT_RATE = new BigDecimal("0.00025"); // 0.025%

    private static final BigDecimal EXCHANGE_TXN_CHARGE_RATE = new BigDecimal("0.0000345"); // 0.00345%
    private static final BigDecimal SEBI_CHARGE_RATE = new BigDecimal("0.000001"); // Rs 10 per crore (10 / 10,000,000)

    private static final BigDecimal STAMP_DUTY_DELIVERY_BUY_RATE = new BigDecimal("0.00015"); // 0.015%
    private static final BigDecimal STAMP_DUTY_INTRADAY_BUY_RATE = new BigDecimal("0.00003"); // 0.003%

    private static final BigDecimal GST_RATE = new BigDecimal("0.18"); // 18%

    /**
     * Delivery Brokerage: Rs 0.00
     */
    public BigDecimal calculateDeliveryBrokerage(BigDecimal tradeValue) {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Intraday Brokerage: lower of 0.03% of trade value or Rs 20 per executed order, with minimum Rs 1.
     */
    public BigDecimal calculateIntradayBrokerage(BigDecimal tradeValue) {
        if (tradeValue == null || tradeValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal computed = tradeValue.multiply(INTRADAY_BROKERAGE_RATE);
        // Apply minimum Rs 1.00
        BigDecimal withMin = computed.max(INTRADAY_BROKERAGE_MIN);
        // Apply maximum cap of Rs 20.00
        BigDecimal finalBrokerage = withMin.min(INTRADAY_BROKERAGE_MAX);
        return finalBrokerage.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Brokerage dispatcher based on product type.
     */
    public BigDecimal calculateBrokerage(ProductType product, BigDecimal tradeValue) {
        if (product == ProductType.DELIVERY) {
            return calculateDeliveryBrokerage(tradeValue);
        } else {
            return calculateIntradayBrokerage(tradeValue);
        }
    }

    /**
     * Delivery STT: 0.1% on buy and sell.
     */
    public BigDecimal calculateDeliverySTT(TransactionSide side, BigDecimal tradeValue) {
        if (tradeValue == null || tradeValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return tradeValue.multiply(DELIVERY_STT_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Intraday STT: 0.025% on sell only; 0% on buy.
     */
    public BigDecimal calculateIntradaySTT(TransactionSide side, BigDecimal tradeValue) {
        if (tradeValue == null || tradeValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (side == TransactionSide.SELL) {
            return tradeValue.multiply(INTRADAY_SELL_STT_RATE).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * STT dispatcher.
     */
    public BigDecimal calculateSTT(ProductType product, TransactionSide side, BigDecimal tradeValue) {
        if (product == ProductType.DELIVERY) {
            return calculateDeliverySTT(side, tradeValue);
        } else {
            return calculateIntradaySTT(side, tradeValue);
        }
    }

    /**
     * Exchange transaction charge: 0.00345% of trade value.
     */
    public BigDecimal calculateExchangeTransactionCharge(BigDecimal tradeValue) {
        if (tradeValue == null || tradeValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return tradeValue.multiply(EXCHANGE_TXN_CHARGE_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * SEBI charge: Rs 10 per crore (0.000001 of trade value).
     */
    public BigDecimal calculateSebiCharge(BigDecimal tradeValue) {
        if (tradeValue == null || tradeValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return tradeValue.multiply(SEBI_CHARGE_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Stamp duty:
     * - Delivery Buy: 0.015%
     * - Intraday Buy: 0.003%
     * - Sell (both Delivery and Intraday): Rs 0.00
     */
    public BigDecimal calculateStampDuty(ProductType product, TransactionSide side, BigDecimal tradeValue) {
        if (tradeValue == null || tradeValue.compareTo(BigDecimal.ZERO) <= 0 || side != TransactionSide.BUY) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (product == ProductType.DELIVERY) {
            return tradeValue.multiply(STAMP_DUTY_DELIVERY_BUY_RATE).setScale(2, RoundingMode.HALF_UP);
        } else {
            return tradeValue.multiply(STAMP_DUTY_INTRADAY_BUY_RATE).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * GST: 18% on (brokerage + exchange transaction charge).
     */
    public BigDecimal calculateGST(BigDecimal brokerage, BigDecimal exchangeCharge) {
        BigDecimal base = (brokerage != null ? brokerage : BigDecimal.ZERO)
                .add(exchangeCharge != null ? exchangeCharge : BigDecimal.ZERO);
        return base.multiply(GST_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Assembles all modular calculations into a complete ChargesBreakdown.
     */
    public ChargesBreakdown calculateAllCharges(ProductType product,
                                               TransactionSide side,
                                               BigDecimal tradeValue,
                                               BigDecimal penalty) {
        BigDecimal brokerage = calculateBrokerage(product, tradeValue);
        BigDecimal stt = calculateSTT(product, side, tradeValue);
        BigDecimal exchangeCharge = calculateExchangeTransactionCharge(tradeValue);
        BigDecimal sebiFee = calculateSebiCharge(tradeValue);
        BigDecimal stampDuty = calculateStampDuty(product, side, tradeValue);
        BigDecimal gst = calculateGST(brokerage, exchangeCharge);
        BigDecimal pen = penalty != null ? penalty.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return new ChargesBreakdown(
                brokerage,
                stt,
                exchangeCharge,
                sebiFee,
                stampDuty,
                gst,
                pen,
                tradeValue,
                side
        );
    }

    public ChargesBreakdown calculateAllCharges(ProductType product, TransactionSide side, BigDecimal tradeValue) {
        return calculateAllCharges(product, side, tradeValue, BigDecimal.ZERO);
    }
}
