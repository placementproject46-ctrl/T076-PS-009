package edu.atria.capstone.service;

import edu.atria.capstone.model.Client;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Negative Ledger Balance Interest Tests (0.05% Daily / EC-06)")
class InterestCalculationTest {

    private ClientRepository clientRepository;
    private LedgerRepository ledgerRepository;
    private InterestCalculationService interestService;

    @BeforeEach
    void setUp() {
        clientRepository = new ClientRepository();
        ledgerRepository = new LedgerRepository();
        interestService = new InterestCalculationService(clientRepository, ledgerRepository);
    }

    @Test
    @DisplayName("Interest starts from the next day (no interest on the day balance becomes negative)")
    void testInterestStartsNextDay() {
        LocalDate day1 = LocalDate.of(2026, 10, 7);
        LocalDate day2 = LocalDate.of(2026, 10, 8);

        Client client = new Client("CL-05", "Keerthi Kamath", "CUIPP5136E", "9010798193",
                new BigDecimal("-10000.00"), LocalDate.of(2026, 9, 30));
        clientRepository.save(client);

        // Day 1 (first negative day): interest must be Rs 0.00
        BigDecimal intDay1 = interestService.calculateAndApplyDailyInterest("CL-05", day1);
        assertEquals(new BigDecimal("0.00"), intDay1);

        // Day 2 (next day): interest = 10,000 * 0.05% = Rs 5.00
        BigDecimal intDay2 = interestService.calculateAndApplyDailyInterest("CL-05", day2);
        assertEquals(new BigDecimal("5.00"), intDay2);
        assertEquals(new BigDecimal("-10005.00"), client.getCashBalance());
    }

    @Test
    @DisplayName("EC-06: Negative balance of exactly Rs 1.00 interest calculation")
    void testExactOneRupeeNegativeBalanceInterest() {
        LocalDate day1 = LocalDate.of(2026, 10, 7);
        LocalDate day2 = LocalDate.of(2026, 10, 8);

        Client client = new Client("CL-05", "Keerthi Kamath", "CUIPP5136E", "9010798193",
                new BigDecimal("-1.00"), LocalDate.of(2026, 9, 30));
        clientRepository.save(client);

        // Day 1: 0 interest
        BigDecimal int1 = interestService.calculateAndApplyDailyInterest("CL-05", day1);
        assertEquals(new BigDecimal("0.00"), int1);

        // Day 2: 1.00 * 0.05% = 0.0005 -> rounds to 0.00 paisa
        BigDecimal int2 = interestService.calculateAndApplyDailyInterest("CL-05", day2);
        assertEquals(new BigDecimal("0.00"), int2);
    }

    @Test
    @DisplayName("Interest stops immediately once incoming funds clear the negative balance")
    void testInterestStopsWhenBalanceCleared() {
        LocalDate day1 = LocalDate.of(2026, 10, 7);
        LocalDate day2 = LocalDate.of(2026, 10, 8);

        Client client = new Client("CL-05", "Keerthi Kamath", "CUIPP5136E", "9010798193",
                new BigDecimal("-1.00"), LocalDate.of(2026, 9, 30));
        clientRepository.save(client);

        interestService.calculateAndApplyDailyInterest("CL-05", day1);

        // Funds-in of Rs 5000 clears the negative balance on day 2 morning
        client.creditCash(new BigDecimal("5000.00"));
        assertEquals(new BigDecimal("4999.00"), client.getCashBalance());

        // At EOD day 2: interest should be 0.00
        BigDecimal intDay2 = interestService.calculateAndApplyDailyInterest("CL-05", day2);
        assertEquals(new BigDecimal("0.00"), intDay2);
    }
}
