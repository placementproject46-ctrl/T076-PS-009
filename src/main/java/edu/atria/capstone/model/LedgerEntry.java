package edu.atria.capstone.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class LedgerEntry {
    private final String entryId;
    private final LocalDateTime timestamp;
    private final LocalDate settlementDate;
    private final String clientId;
    private final String entryType;
    private final BigDecimal debit;
    private final BigDecimal credit;
    private final BigDecimal runningBalance;
    private final String narration;
    private final String referenceId;

    public LedgerEntry(String entryId,
                       LocalDateTime timestamp,
                       LocalDate settlementDate,
                       String clientId,
                       String entryType,
                       BigDecimal debit,
                       BigDecimal credit,
                       BigDecimal runningBalance,
                       String narration,
                       String referenceId) {
        this.entryId = entryId;
        this.timestamp = timestamp;
        this.settlementDate = settlementDate;
        this.clientId = clientId;
        this.entryType = entryType;
        this.debit = debit != null ? debit.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.credit = credit != null ? credit.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.runningBalance = runningBalance != null ? runningBalance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.narration = narration;
        this.referenceId = referenceId;
    }

    public String getEntryId() {
        return entryId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public String getClientId() {
        return clientId;
    }

    public String getEntryType() {
        return entryType;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public BigDecimal getRunningBalance() {
        return runningBalance;
    }

    public String getNarration() {
        return narration;
    }

    public String getReferenceId() {
        return referenceId;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | Type: %s | Dr: %s | Cr: %s | Bal: %s | Ref: %s | %s",
                timestamp, clientId, entryType, debit, credit, runningBalance, referenceId, narration);
    }
}
