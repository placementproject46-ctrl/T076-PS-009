package edu.atria.capstone.service;

import edu.atria.capstone.model.Client;
import edu.atria.capstone.model.LedgerEntry;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.LedgerRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class InterestCalculationService {
    private static final BigDecimal DAILY_INTEREST_RATE = new BigDecimal("0.0005"); // 0.05% per day

    private final ClientRepository clientRepository;
    private final LedgerRepository ledgerRepository;

    // Track the date on which a client's balance first became negative
    private final Map<String, LocalDate> negativeSinceDates = new HashMap<>();

    public InterestCalculationService(ClientRepository clientRepository, LedgerRepository ledgerRepository) {
        this.clientRepository = clientRepository;
        this.ledgerRepository = ledgerRepository;
    }

    /**
     * Evaluates and applies daily negative ledger interest at EOD.
     * Returns total interest charged for this client on this date.
     */
    public BigDecimal calculateAndApplyDailyInterest(String clientId, LocalDate currentDate) {
        Client client = clientRepository.findById(clientId).orElse(null);
        if (client == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal balance = client.getCashBalance();

        if (balance.compareTo(BigDecimal.ZERO) >= 0) {
            // Balance is positive or zero; clear any negative history
            negativeSinceDates.remove(clientId);
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Balance is negative
        LocalDate firstNegativeDate = negativeSinceDates.get(clientId);
        if (firstNegativeDate == null) {
            // First day balance is observed negative; interest starts from the next day
            negativeSinceDates.put(clientId, currentDate);
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (currentDate.isAfter(firstNegativeDate)) {
            // Interest starts from next day
            BigDecimal negativeAmount = balance.abs();
            BigDecimal interest = negativeAmount.multiply(DAILY_INTEREST_RATE).setScale(2, RoundingMode.HALF_UP);

            if (interest.compareTo(BigDecimal.ZERO) > 0) {
                client.debitCash(interest);
                ledgerRepository.save(new LedgerEntry(
                        ledgerRepository.nextEntryId(),
                        LocalDateTime.of(currentDate, java.time.LocalTime.of(16, 0, 0)),
                        currentDate,
                        clientId,
                        "NEGATIVE_BALANCE_INTEREST",
                        interest,
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        client.getCashBalance(),
                        String.format("0.05%% daily interest on negative balance of Rs %s", negativeAmount),
                        "INT-" + currentDate
                ));
            }
            return interest;
        }

        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public LocalDate getNegativeSinceDate(String clientId) {
        return negativeSinceDates.get(clientId);
    }

    public void clear() {
        negativeSinceDates.clear();
    }
}
