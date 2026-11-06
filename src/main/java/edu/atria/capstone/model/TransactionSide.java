package edu.atria.capstone.model;

public enum TransactionSide {
    BUY,
    SELL;

    public static TransactionSide fromString(String val) {
        if (val == null) return null;
        return TransactionSide.valueOf(val.trim().toUpperCase());
    }
}
