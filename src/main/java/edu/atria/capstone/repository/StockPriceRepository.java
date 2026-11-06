package edu.atria.capstone.repository;

import edu.atria.capstone.model.StockPrice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class StockPriceRepository {
    // Map of symbol -> TreeMap<tradeDate, closePrice>
    private final Map<String, TreeMap<LocalDate, BigDecimal>> priceHistory = new HashMap<>();

    // Latest live/intraday prices
    private final Map<String, BigDecimal> currentIntradayPrices = new HashMap<>();

    public void save(StockPrice stockPrice) {
        if (stockPrice == null) return;
        String sym = stockPrice.getSymbol().toUpperCase();
        priceHistory.computeIfAbsent(sym, k -> new TreeMap<>())
                .put(stockPrice.getTradeDate(), stockPrice.getClosePrice());
    }

    public void save(LocalDate date, String symbol, BigDecimal price) {
        save(new StockPrice(date, symbol, price));
    }

    public void updateIntradayPrice(String symbol, BigDecimal price) {
        if (symbol == null || price == null) return;
        currentIntradayPrices.put(symbol.toUpperCase(), price);
    }

    public Optional<BigDecimal> getClosePrice(LocalDate date, String symbol) {
        if (date == null || symbol == null) return Optional.empty();
        TreeMap<LocalDate, BigDecimal> history = priceHistory.get(symbol.toUpperCase());
        if (history == null) return Optional.empty();
        return Optional.ofNullable(history.get(date));
    }

    /**
     * Gets previous day's close price strictly before the given date.
     * This is required for the +/- 10% price band validation.
     */
    public Optional<BigDecimal> getPreviousClosePrice(LocalDate date, String symbol) {
        if (date == null || symbol == null) return Optional.empty();
        TreeMap<LocalDate, BigDecimal> history = priceHistory.get(symbol.toUpperCase());
        if (history == null) return Optional.empty();
        
        Map.Entry<LocalDate, BigDecimal> lowerEntry = history.lowerEntry(date);
        if (lowerEntry != null) {
            return Optional.of(lowerEntry.getValue());
        }
        return Optional.empty();
    }

    /**
     * Gets the current market price / LTP:
     * 1. Check intraday price mark if available
     * 2. Otherwise close price on date
     * 3. Otherwise latest close price on or before date
     */
    public Optional<BigDecimal> getCurrentOrLatestPrice(LocalDate date, String symbol) {
        if (symbol == null) return Optional.empty();
        String sym = symbol.toUpperCase();
        
        if (currentIntradayPrices.containsKey(sym)) {
            return Optional.of(currentIntradayPrices.get(sym));
        }

        TreeMap<LocalDate, BigDecimal> history = priceHistory.get(sym);
        if (history == null || history.isEmpty()) return Optional.empty();

        if (date != null) {
            Map.Entry<LocalDate, BigDecimal> floor = history.floorEntry(date);
            if (floor != null) {
                return Optional.of(floor.getValue());
            }
        }
        return Optional.ofNullable(history.lastEntry().getValue());
    }

    public boolean hasPriceForSymbol(String symbol) {
        if (symbol == null) return false;
        return priceHistory.containsKey(symbol.toUpperCase());
    }

    public void clearIntradayPrices() {
        currentIntradayPrices.clear();
    }

    public void clear() {
        priceHistory.clear();
        currentIntradayPrices.clear();
    }
}
