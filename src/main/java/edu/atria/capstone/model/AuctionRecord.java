package edu.atria.capstone.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class AuctionRecord {
    private final String auctionId;
    private final LocalDate date;
    private final String clientId;
    private final String symbol;
    private final int shortfallQuantity;
    private final BigDecimal closePrice;
    private final BigDecimal penaltyRate; // 20%
    private final BigDecimal shortfallValue;
    private final BigDecimal penaltyAmount;
    private final BigDecimal totalDebit;

    public AuctionRecord(String auctionId,
                         LocalDate date,
                         String clientId,
                         String symbol,
                         int shortfallQuantity,
                         BigDecimal closePrice) {
        this.auctionId = auctionId;
        this.date = date;
        this.clientId = clientId;
        this.symbol = symbol;
        this.shortfallQuantity = shortfallQuantity;
        this.closePrice = closePrice != null ? closePrice.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.penaltyRate = new BigDecimal("0.20"); // 20% penalty
        this.shortfallValue = this.closePrice.multiply(BigDecimal.valueOf(shortfallQuantity)).setScale(2, RoundingMode.HALF_UP);
        this.penaltyAmount = this.shortfallValue.multiply(penaltyRate).setScale(2, RoundingMode.HALF_UP);
        this.totalDebit = this.shortfallValue.add(this.penaltyAmount).setScale(2, RoundingMode.HALF_UP);
    }

    public String getAuctionId() {
        return auctionId;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getShortfallQuantity() {
        return shortfallQuantity;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public BigDecimal getShortfallValue() {
        return shortfallValue;
    }

    public BigDecimal getPenaltyAmount() {
        return penaltyAmount;
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    @Override
    public String toString() {
        return String.format("Auction[%s] %s | %d %s @ %s | Shortfall: %s | Penalty(20%%): %s | TotalDebit: %s",
                auctionId, clientId, shortfallQuantity, symbol, closePrice, shortfallValue, penaltyAmount, totalDebit);
    }
}
