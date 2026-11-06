package edu.atria.capstone.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OrderDecisionRecord {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String refId;
    private final LocalDateTime timestamp;
    private final String clientId;
    private final String symbol;
    private final ActionType action;
    private final OrderDecision decision;
    private final String ruleIds;
    private final int quantity;
    private final BigDecimal price;
    private final BigDecimal tradeValue;
    private final BigDecimal requiredMargin;
    private final BigDecimal availableMargin;

    public OrderDecisionRecord(String refId,
                               LocalDateTime timestamp,
                               String clientId,
                               String symbol,
                               ActionType action,
                               OrderDecision decision,
                               String ruleIds,
                               int quantity,
                               BigDecimal price,
                               BigDecimal tradeValue,
                               BigDecimal requiredMargin,
                               BigDecimal availableMargin) {
        this.refId = refId;
        this.timestamp = timestamp;
        this.clientId = clientId;
        this.symbol = symbol != null ? symbol : "";
        this.action = action;
        this.decision = decision;
        this.ruleIds = ruleIds != null ? ruleIds : "";
        this.quantity = quantity;
        this.price = price != null ? price : BigDecimal.ZERO;
        this.tradeValue = tradeValue != null ? tradeValue : BigDecimal.ZERO;
        this.requiredMargin = requiredMargin != null ? requiredMargin : BigDecimal.ZERO;
        this.availableMargin = availableMargin != null ? availableMargin : BigDecimal.ZERO;
    }

    public String getRefId() {
        return refId;
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

    public ActionType getAction() {
        return action;
    }

    public OrderDecision getDecision() {
        return decision;
    }

    public String getRuleIds() {
        return ruleIds;
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

    public BigDecimal getAvailableMargin() {
        return availableMargin;
    }

    public String toCsvRow() {
        return String.join(",",
                refId,
                timestamp.format(FORMATTER),
                clientId,
                symbol,
                action.name(),
                decision.name(),
                ruleIds,
                String.valueOf(quantity),
                price.toPlainString(),
                tradeValue.toPlainString(),
                requiredMargin.toPlainString(),
                availableMargin.toPlainString()
        );
    }
}
