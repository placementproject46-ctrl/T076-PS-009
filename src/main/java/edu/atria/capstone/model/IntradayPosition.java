package edu.atria.capstone.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public class IntradayPosition {
    private final String clientId;
    private final String symbol;
    private int netQuantity; // Positive for LONG, negative for SHORT
    private BigDecimal avgCost;
    private String openingOrderId;
    private LocalDateTime openingTimestamp;

    public IntradayPosition(String clientId, String symbol, int quantity, TransactionSide side, BigDecimal price, String orderId, LocalDateTime timestamp) {
        this.clientId = clientId;
        this.symbol = symbol;
        this.netQuantity = side == TransactionSide.BUY ? quantity : -quantity;
        this.avgCost = price != null ? price.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.openingOrderId = orderId;
        this.openingTimestamp = timestamp;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getNetQuantity() {
        return netQuantity;
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public String getOpeningOrderId() {
        return openingOrderId;
    }

    public LocalDateTime getOpeningTimestamp() {
        return openingTimestamp;
    }

    public boolean isClosed() {
        return netQuantity == 0;
    }

    public boolean isLong() {
        return netQuantity > 0;
    }

    public boolean isShort() {
        return netQuantity < 0;
    }

    /**
     * Adds an execution to this position (either increasing or reducing/reversing).
     * Returns the realized P&L from this trade (if position was reduced).
     */
    public BigDecimal addTrade(TransactionSide side, int quantity, BigDecimal price, String orderId, LocalDateTime timestamp) {
        int tradeQty = side == TransactionSide.BUY ? quantity : -quantity;
        BigDecimal executedPrice = price.setScale(2, RoundingMode.HALF_UP);
        BigDecimal realizedPnl = BigDecimal.ZERO;

        if (netQuantity == 0) {
            this.netQuantity = tradeQty;
            this.avgCost = executedPrice;
            this.openingOrderId = orderId;
            this.openingTimestamp = timestamp;
            return realizedPnl;
        }

        // Same direction: increase position and recalculate weighted average cost
        if ((netQuantity > 0 && tradeQty > 0) || (netQuantity < 0 && tradeQty < 0)) {
            int oldAbsQty = Math.abs(netQuantity);
            int newAbsQty = Math.abs(tradeQty);
            int totalAbsQty = oldAbsQty + newAbsQty;

            BigDecimal oldCostTotal = avgCost.multiply(BigDecimal.valueOf(oldAbsQty));
            BigDecimal newCostTotal = executedPrice.multiply(BigDecimal.valueOf(newAbsQty));

            this.avgCost = oldCostTotal.add(newCostTotal)
                    .divide(BigDecimal.valueOf(totalAbsQty), 2, RoundingMode.HALF_UP);
            this.netQuantity += tradeQty;
            return realizedPnl;
        }

        // Opposite direction: reduce or reverse position
        int oldAbs = Math.abs(netQuantity);
        int tradeAbs = Math.abs(tradeQty);
        int closedQty = Math.min(oldAbs, tradeAbs);

        // Realized P&L = (Sell Price - Buy Price) * Closed Qty
        if (netQuantity > 0) { // was long, now selling
            realizedPnl = executedPrice.subtract(avgCost)
                    .multiply(BigDecimal.valueOf(closedQty))
                    .setScale(2, RoundingMode.HALF_UP);
        } else { // was short, now buying
            realizedPnl = avgCost.subtract(executedPrice)
                    .multiply(BigDecimal.valueOf(closedQty))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        this.netQuantity += tradeQty;
        if (this.netQuantity != 0 && ((oldAbs < tradeAbs))) {
            // Position reversed direction
            this.avgCost = executedPrice;
            this.openingOrderId = orderId;
            this.openingTimestamp = timestamp;
        }

        return realizedPnl;
    }

    /**
     * Calculates mark-to-market unrealized P&L using current price.
     */
    public BigDecimal calculateMtm(BigDecimal currentPrice) {
        if (netQuantity == 0 || currentPrice == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        int absQty = Math.abs(netQuantity);
        if (netQuantity > 0) {
            return currentPrice.subtract(avgCost)
                    .multiply(BigDecimal.valueOf(absQty))
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            return avgCost.subtract(currentPrice)
                    .multiply(BigDecimal.valueOf(absQty))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * Position market value = |netQuantity| * currentPrice.
     */
    public BigDecimal getPositionValue(BigDecimal currentPrice) {
        if (currentPrice == null || netQuantity == 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return currentPrice.multiply(BigDecimal.valueOf(Math.abs(netQuantity))).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return String.format("[%s %s] NetQty: %d | AvgCost: %s | OpenOrd: %s",
                clientId, symbol, netQuantity, avgCost, openingOrderId);
    }
}
