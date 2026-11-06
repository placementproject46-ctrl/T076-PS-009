package edu.atria.capstone.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

public class Client {
    private final String clientId;
    private final String clientName;
    private final String pan;
    private final String mobile;
    private BigDecimal cashBalance;
    private final LocalDate openingDate;
    private final Map<String, Holding> holdings = new TreeMap<>();

    public Client(String clientId, String clientName, String pan, String mobile, BigDecimal cashBalance, LocalDate openingDate) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.pan = pan;
        this.mobile = mobile;
        this.cashBalance = cashBalance != null ? cashBalance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.openingDate = openingDate;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public String getPan() {
        return pan;
    }

    public String getMobile() {
        return mobile;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance != null ? cashBalance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public LocalDate getOpeningDate() {
        return openingDate;
    }

    public Map<String, Holding> getHoldings() {
        return Collections.unmodifiableMap(holdings);
    }

    public Holding getHolding(String symbol) {
        return holdings.get(symbol);
    }

    public int getFreeHoldingQuantity(String symbol) {
        Holding h = holdings.get(symbol);
        return h == null ? 0 : h.getFreeQty();
    }

    public int getTotalHoldingQuantity(String symbol) {
        Holding h = holdings.get(symbol);
        return h == null ? 0 : h.getQuantity();
    }

    public int getPledgedQuantity(String symbol) {
        Holding h = holdings.get(symbol);
        return h == null ? 0 : h.getPledgedQty();
    }

    public void addOrUpdateHolding(String symbol, int qty, int pledgedQty, BigDecimal avgCost) {
        Holding existing = holdings.get(symbol);
        if (existing == null) {
            holdings.put(symbol, new Holding(symbol, qty, pledgedQty, avgCost));
        } else {
            existing.setQuantity(qty);
            existing.setPledgedQty(pledgedQty);
            if (avgCost != null) existing.setAvgCost(avgCost);
        }
    }

    public void addShares(String symbol, int qty, BigDecimal price) {
        Holding existing = holdings.get(symbol);
        if (existing == null) {
            holdings.put(symbol, new Holding(symbol, qty, 0, price));
        } else {
            existing.addShares(qty, price);
        }
    }

    public boolean deductShares(String symbol, int qty) {
        Holding existing = holdings.get(symbol);
        if (existing == null) return false;
        boolean success = existing.deductShares(qty);
        if (success && existing.getQuantity() == 0 && existing.getPledgedQty() == 0) {
            holdings.remove(symbol);
        }
        return success;
    }

    public boolean pledgeShares(String symbol, int qty) {
        Holding existing = holdings.get(symbol);
        if (existing == null) return false;
        return existing.pledgeShares(qty);
    }

    public boolean unpledgeShares(String symbol, int qty) {
        Holding existing = holdings.get(symbol);
        if (existing == null) return false;
        return existing.unpledgeShares(qty);
    }

    public void creditCash(BigDecimal amount) {
        if (amount == null) return;
        this.cashBalance = this.cashBalance.add(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public void debitCash(BigDecimal amount) {
        if (amount == null) return;
        this.cashBalance = this.cashBalance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Client client)) return false;
        return Objects.equals(clientId, client.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId);
    }

    @Override
    public String toString() {
        return "Client{" +
                "clientId='" + clientId + '\'' +
                ", clientName='" + clientName + '\'' +
                ", pan='" + pan + '\'' +
                ", cashBalance=" + cashBalance +
                ", holdingsCount=" + holdings.size() +
                '}';
    }
}
