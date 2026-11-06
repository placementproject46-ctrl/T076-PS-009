package edu.atria.capstone.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Order {
    private final String orderId;
    private final LocalDateTime timestamp;
    private final String clientId;
    private final String symbol;
    private final TransactionSide side;
    private final ProductType product;
    private final int quantity;
    private final BigDecimal price;
    private final BigDecimal tradeValue;

    private BigDecimal requiredMargin;
    private OrderDecision decision;
    private final List<String> ruleIds = new ArrayList<>();
    private String rejectionReason;
    private ChargesBreakdown chargesBreakdown;

    public Order(String orderId,
                 LocalDateTime timestamp,
                 String clientId,
                 String symbol,
                 TransactionSide side,
                 ProductType product,
                 int quantity,
                 BigDecimal price) {
        this.orderId = orderId;
        this.timestamp = timestamp;
        this.clientId = clientId;
        this.symbol = symbol;
        this.side = side;
        this.product = product;
        this.quantity = quantity;
        this.price = price != null ? price.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.tradeValue = this.price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public String getOrderId() {
        return orderId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSymbol() {
        return symbol;
    }

    public TransactionSide getSide() {
        return side;
    }

    public ProductType getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getTradeValue() {
        return tradeValue;
    }

    public BigDecimal getRequiredMargin() {
        return requiredMargin;
    }

    public void setRequiredMargin(BigDecimal requiredMargin) {
        this.requiredMargin = requiredMargin != null ? requiredMargin.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public OrderDecision getDecision() {
        return decision;
    }

    public void setDecision(OrderDecision decision) {
        this.decision = decision;
    }

    public List<String> getRuleIds() {
        return ruleIds;
    }

    public void addRuleId(String ruleId) {
        this.ruleIds.add(ruleId);
    }

    public void setRuleIds(List<String> ruleIds) {
        this.ruleIds.clear();
        if (ruleIds != null) {
            this.ruleIds.addAll(ruleIds);
        }
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public ChargesBreakdown getChargesBreakdown() {
        return chargesBreakdown;
    }

    public void setChargesBreakdown(ChargesBreakdown chargesBreakdown) {
        this.chargesBreakdown = chargesBreakdown;
    }

    public boolean isAccepted() {
        return decision == OrderDecision.ACCEPTED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order order)) return false;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", clientId='" + clientId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", product=" + product +
                ", quantity=" + quantity +
                ", price=" + price +
                ", tradeValue=" + tradeValue +
                ", decision=" + decision +
                ", ruleIds=" + ruleIds +
                '}';
    }
}
