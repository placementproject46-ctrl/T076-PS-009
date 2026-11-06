package edu.atria.capstone.model;

import java.util.Objects;

public class Scrip {
    private final String symbol;
    private final String companyName;
    private PledgeGroup pledgeGroup;
    private final int lotSize;

    public Scrip(String symbol, String companyName, PledgeGroup pledgeGroup, int lotSize) {
        this.symbol = symbol;
        this.companyName = companyName;
        this.pledgeGroup = pledgeGroup;
        this.lotSize = lotSize;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getCompanyName() {
        return companyName;
    }

    public PledgeGroup getPledgeGroup() {
        return pledgeGroup;
    }

    public void setPledgeGroup(PledgeGroup pledgeGroup) {
        this.pledgeGroup = pledgeGroup;
    }

    public int getLotSize() {
        return lotSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Scrip scrip)) return false;
        return Objects.equals(symbol, scrip.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol);
    }

    @Override
    public String toString() {
        return "Scrip{" +
                "symbol='" + symbol + '\'' +
                ", companyName='" + companyName + '\'' +
                ", pledgeGroup=" + pledgeGroup +
                ", lotSize=" + lotSize +
                '}';
    }
}
