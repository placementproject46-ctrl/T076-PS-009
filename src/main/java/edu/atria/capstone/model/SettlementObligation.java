package edu.atria.capstone.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SettlementObligation {
    private final String obligationId;
    private final String orderId;
    private final LocalDate tradeDate;
    private final LocalDate settlementDate;
    private final String clientId;
    private final String symbol;
    private final TransactionSide side;
    private final int quantity;
    private final BigDecimal price;
    private final BigDecimal tradeValue;
    private final ChargesBreakdown charges;
    private final BigDecimal netAmount;
    private boolean settled;

    public SettlementObligation(String obligationId,
                                String orderId,
                                LocalDate tradeDate,
                                LocalDate settlementDate,
                                String clientId,
                                String symbol,
                                TransactionSide side,
                                int quantity,
                                BigDecimal price,
                                BigDecimal tradeValue,
                                ChargesBreakdown charges) {
        this.obligationId = obligationId;
        this.orderId = orderId;
        this.tradeDate = tradeDate;
        this.settlementDate = settlementDate;
        this.clientId = clientId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.tradeValue = tradeValue;
        this.charges = charges;
        this.netAmount = charges != null ? charges.getNetAmount() : tradeValue;
        this.settled = false;
    }

    public String getObligationId() {
        return obligationId;
    }

    public String getOrderId() {
        return orderId;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
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

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getTradeValue() {
        return tradeValue;
    }

    public ChargesBreakdown getCharges() {
        return charges;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public boolean isSettled() {
        return settled;
    }

    public void setSettled(boolean settled) {
        this.settled = settled;
    }

    @Override
    public String toString() {
        return String.format("Obligation[%s] %s | SettleDate: %s | %s %d %s @ %s | Net: %s | Settled: %b",
                obligationId, clientId, settlementDate, side, quantity, symbol, price, netAmount, settled);
    }
}
