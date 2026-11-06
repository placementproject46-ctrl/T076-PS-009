package edu.atria.capstone.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class Holding {
    private final String symbol;
    private int quantity;
    private int pledgedQty;
    private BigDecimal avgCost;

    public Holding(String symbol, int quantity, int pledgedQty, BigDecimal avgCost) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.pledgedQty = pledgedQty;
        this.avgCost = avgCost != null ? avgCost.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public String getSymbol() {
        return symbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getPledgedQty() {
        return pledgedQty;
    }

    public void setPledgedQty(int pledgedQty) {
        this.pledgedQty = pledgedQty;
    }

    public int getFreeQty() {
        return Math.max(0, quantity - pledgedQty);
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public void setAvgCost(BigDecimal avgCost) {
        this.avgCost = avgCost;
    }

    /**
     * Updates holding when new delivery shares are purchased.
     * Computes weighted average cost: ((oldQty * oldAvg) + (newQty * newPrice)) / (oldQty + newQty)
     */
    public void addShares(int addedQty, BigDecimal buyPrice) {
        if (addedQty <= 0) return;
        BigDecimal currentTotalCost = avgCost.multiply(BigDecimal.valueOf(quantity));
        BigDecimal newAdditionCost = buyPrice.multiply(BigDecimal.valueOf(addedQty));
        int totalQty = quantity + addedQty;
        this.avgCost = currentTotalCost.add(newAdditionCost)
                .divide(BigDecimal.valueOf(totalQty), 2, RoundingMode.HALF_UP);
        this.quantity = totalQty;
    }

    /**
     * Deducts shares when delivery shares are sold.
     */
    public boolean deductShares(int soldQty) {
        if (soldQty > getFreeQty()) {
            return false;
        }
        this.quantity -= soldQty;
        return true;
    }

    public boolean pledgeShares(int qty) {
        if (qty <= 0 || qty > getFreeQty()) {
            return false;
        }
        this.pledgedQty += qty;
        return true;
    }

    public boolean unpledgeShares(int qty) {
        if (qty <= 0 || qty > this.pledgedQty) {
            return false;
        }
        this.pledgedQty -= qty;
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Holding holding)) return false;
        return Objects.equals(symbol, holding.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol);
    }

    @Override
    public String toString() {
        return "Holding{" +
                "symbol='" + symbol + '\'' +
                ", quantity=" + quantity +
                ", pledgedQty=" + pledgedQty +
                ", freeQty=" + getFreeQty() +
                ", avgCost=" + avgCost.setScale(2, RoundingMode.HALF_UP) +
                '}';
    }
}
