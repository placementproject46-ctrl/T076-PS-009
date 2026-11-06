package edu.atria.capstone.model;

import java.math.BigDecimal;

/**
 * Haircut classes:
 * GROUP_A: 10% haircut (collateral factor = 0.90)
 * GROUP_B: 25% haircut (collateral factor = 0.75)
 * GROUP_C: 100% haircut (not pledgeable / nil collateral value)
 */
public enum PledgeGroup {
    GROUP_A(new BigDecimal("0.10"), true),
    GROUP_B(new BigDecimal("0.25"), true),
    GROUP_C(new BigDecimal("1.00"), false);

    private final BigDecimal haircutRate;
    private final boolean isPledgeable;

    PledgeGroup(BigDecimal haircutRate, boolean isPledgeable) {
        this.haircutRate = haircutRate;
        this.isPledgeable = isPledgeable;
    }

    public BigDecimal getHaircutRate() {
        return haircutRate;
    }

    public BigDecimal getCollateralFactor() {
        return BigDecimal.ONE.subtract(haircutRate);
    }

    public boolean isPledgeable() {
        return isPledgeable;
    }

    public static PledgeGroup fromString(String val) {
        if (val == null) return null;
        return PledgeGroup.valueOf(val.trim().toUpperCase());
    }
}
