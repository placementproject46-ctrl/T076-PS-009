package edu.atria.capstone.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ContractNote {
    private final String refId;
    private final LocalDate tradeDate;
    private final String clientId;
    private final String symbol;
    private final TransactionSide side;
    private final ProductType product;
    private final int quantity;
    private final BigDecimal price;
    private final BigDecimal tradeValue;
    private final ChargesBreakdown charges;

    public ContractNote(String refId,
                        LocalDate tradeDate,
                        String clientId,
                        String symbol,
                        TransactionSide side,
                        ProductType product,
                        int quantity,
                        BigDecimal price,
                        BigDecimal tradeValue,
                        ChargesBreakdown charges) {
        this.refId = refId;
        this.tradeDate = tradeDate;
        this.clientId = clientId;
        this.symbol = symbol;
        this.side = side;
        this.product = product;
        this.quantity = quantity;
        this.price = price;
        this.tradeValue = tradeValue;
        this.charges = charges;
    }

    public String getRefId() {
        return refId;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
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

    public ChargesBreakdown getCharges() {
        return charges;
    }

    /**
     * Converts to CSV row according to README specification:
     * ref_id,trade_date,client_id,symbol,side,product,quantity,price,trade_value,brokerage,stt,exchange_charge,sebi_fee,stamp_duty,gst,penalty,total_charges,net_amount
     */
    public String toCsvRow() {
        return String.join(",",
                refId,
                tradeDate.toString(),
                clientId,
                symbol,
                side.name(),
                product.name(),
                String.valueOf(quantity),
                price.toPlainString(),
                tradeValue.toPlainString(),
                charges.getBrokerage().toPlainString(),
                charges.getStt().toPlainString(),
                charges.getExchangeCharge().toPlainString(),
                charges.getSebiFee().toPlainString(),
                charges.getStampDuty().toPlainString(),
                charges.getGst().toPlainString(),
                charges.getPenalty().toPlainString(),
                charges.getTotalCharges().toPlainString(),
                charges.getNetAmount().toPlainString()
        );
    }
}
