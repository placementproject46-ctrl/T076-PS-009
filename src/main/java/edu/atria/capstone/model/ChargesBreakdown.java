package edu.atria.capstone.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ChargesBreakdown {
    private final BigDecimal brokerage;
    private final BigDecimal stt;
    private final BigDecimal exchangeCharge;
    private final BigDecimal sebiFee;
    private final BigDecimal stampDuty;
    private final BigDecimal gst;
    private final BigDecimal penalty;
    private final BigDecimal totalCharges;
    private final BigDecimal netAmount;

    public ChargesBreakdown(BigDecimal brokerage,
                            BigDecimal stt,
                            BigDecimal exchangeCharge,
                            BigDecimal sebiFee,
                            BigDecimal stampDuty,
                            BigDecimal gst,
                            BigDecimal penalty,
                            BigDecimal tradeValue,
                            TransactionSide side) {
        this.brokerage = round(brokerage);
        this.stt = round(stt);
        this.exchangeCharge = round(exchangeCharge);
        this.sebiFee = round(sebiFee);
        this.stampDuty = round(stampDuty);
        this.gst = round(gst);
        this.penalty = round(penalty);

        this.totalCharges = this.brokerage
                .add(this.stt)
                .add(this.exchangeCharge)
                .add(this.sebiFee)
                .add(this.stampDuty)
                .add(this.gst)
                .add(this.penalty)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal roundedTradeVal = round(tradeValue);
        if (side == TransactionSide.BUY) {
            this.netAmount = roundedTradeVal.add(this.totalCharges).setScale(2, RoundingMode.HALF_UP);
        } else {
            this.netAmount = roundedTradeVal.subtract(this.totalCharges).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static BigDecimal round(BigDecimal val) {
        if (val == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return val.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getBrokerage() {
        return brokerage;
    }

    public BigDecimal getStt() {
        return stt;
    }

    public BigDecimal getExchangeCharge() {
        return exchangeCharge;
    }

    public BigDecimal getSebiFee() {
        return sebiFee;
    }

    public BigDecimal getStampDuty() {
        return stampDuty;
    }

    public BigDecimal getGst() {
        return gst;
    }

    public BigDecimal getPenalty() {
        return penalty;
    }

    public BigDecimal getTotalCharges() {
        return totalCharges;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    @Override
    public String toString() {
        return "ChargesBreakdown{" +
                "brokerage=" + brokerage +
                ", stt=" + stt +
                ", exchangeCharge=" + exchangeCharge +
                ", sebiFee=" + sebiFee +
                ", stampDuty=" + stampDuty +
                ", gst=" + gst +
                ", penalty=" + penalty +
                ", totalCharges=" + totalCharges +
                ", netAmount=" + netAmount +
                '}';
    }
}
