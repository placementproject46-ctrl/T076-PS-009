package edu.atria.capstone.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class EodRecord {
    private final LocalDate date;
    private final String clientId;
    private final BigDecimal ledgerBalance;
    private final BigDecimal collateralValue;
    private final BigDecimal intradayMtm;
    private final BigDecimal usableMargin;
    private final BigDecimal usedMargin;
    private final BigDecimal interestCharged;
    private final int openIntradayPositions;

    public EodRecord(LocalDate date,
                     String clientId,
                     BigDecimal ledgerBalance,
                     BigDecimal collateralValue,
                     BigDecimal intradayMtm,
                     BigDecimal usableMargin,
                     BigDecimal usedMargin,
                     BigDecimal interestCharged,
                     int openIntradayPositions) {
        this.date = date;
        this.clientId = clientId;
        this.ledgerBalance = ledgerBalance != null ? ledgerBalance : BigDecimal.ZERO;
        this.collateralValue = collateralValue != null ? collateralValue : BigDecimal.ZERO;
        this.intradayMtm = intradayMtm != null ? intradayMtm : BigDecimal.ZERO;
        this.usableMargin = usableMargin != null ? usableMargin : BigDecimal.ZERO;
        this.usedMargin = usedMargin != null ? usedMargin : BigDecimal.ZERO;
        this.interestCharged = interestCharged != null ? interestCharged : BigDecimal.ZERO;
        this.openIntradayPositions = openIntradayPositions;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getClientId() {
        return clientId;
    }

    public BigDecimal getLedgerBalance() {
        return ledgerBalance;
    }

    public BigDecimal getCollateralValue() {
        return collateralValue;
    }

    public BigDecimal getIntradayMtm() {
        return intradayMtm;
    }

    public BigDecimal getUsableMargin() {
        return usableMargin;
    }

    public BigDecimal getUsedMargin() {
        return usedMargin;
    }

    public BigDecimal getInterestCharged() {
        return interestCharged;
    }

    public int getOpenIntradayPositions() {
        return openIntradayPositions;
    }

    /**
     * CSV output format expected at evaluation:
     * date,client_id,ledger_balance,collateral_value,intraday_mtm,usable_margin,used_margin,interest_charged,open_intraday_positions
     */
    public String toCsvRow() {
        return String.join(",",
                date.toString(),
                clientId,
                ledgerBalance.toPlainString(),
                collateralValue.toPlainString(),
                intradayMtm.toPlainString(),
                usableMargin.toPlainString(),
                usedMargin.toPlainString(),
                interestCharged.toPlainString(),
                String.valueOf(openIntradayPositions)
        );
    }
}
