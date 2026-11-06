package edu.atria.capstone.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class StockPrice {
    private final LocalDate tradeDate;
    private final String symbol;
    private final BigDecimal closePrice;

    public StockPrice(LocalDate tradeDate, String symbol, BigDecimal closePrice) {
        this.tradeDate = tradeDate;
        this.symbol = symbol;
        this.closePrice = closePrice;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockPrice that)) return false;
        return Objects.equals(tradeDate, that.tradeDate) && Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeDate, symbol);
    }

    @Override
    public String toString() {
        return "StockPrice{" +
                "tradeDate=" + tradeDate +
                ", symbol='" + symbol + '\'' +
                ", closePrice=" + closePrice +
                '}';
    }
}
