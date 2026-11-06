package edu.atria.capstone.model;

public enum ProductType {
    DELIVERY,
    INTRADAY;

    public static ProductType fromString(String val) {
        if (val == null) return null;
        return ProductType.valueOf(val.trim().toUpperCase());
    }
}
