package edu.atria.capstone.service;

import edu.atria.capstone.model.Holiday;
import edu.atria.capstone.model.ValidationResult;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.HolidayRepository;
import edu.atria.capstone.repository.ScripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Centralized Input Validation Tests (Phase 3)")
class InputValidationTest {

    private HolidayRepository holidayRepository;
    private InputValidationService inputValidationService;

    @BeforeEach
    void setUp() {
        ClientRepository clientRepository = new ClientRepository();
        ScripRepository scripRepository = new ScripRepository();
        holidayRepository = new HolidayRepository();
        inputValidationService = new InputValidationService(clientRepository, scripRepository, holidayRepository);

        holidayRepository.save(new Holiday(LocalDate.of(2026, 10, 20), "Dussehra"));
    }

    @Test
    @DisplayName("Valid ORDER command syntax passes input validation")
    void testValidOrderSyntaxPasses() {
        String line = "ORDER|ORD-0001|2026-10-05 10:05:00|CL-06|MALNADAGR|BUY|INTRADAY|1000|500.00";
        ValidationResult res = inputValidationService.validateCommandLine(line);
        assertTrue(res.isValid());
    }

    @Test
    @DisplayName("INP_VAL_007: Rejects unknown command name safely")
    void testUnknownCommandRejection() {
        String line = "FLY_TO_MOON|ARG1|ARG2";
        ValidationResult res = inputValidationService.validateCommandLine(line);
        assertFalse(res.isValid());
        assertTrue(res.getRuleIdsString().contains(InputValidationService.INP_VAL_UNKNOWN_COMMAND));
    }

    @Test
    @DisplayName("INP_VAL_008: Rejects missing command parameters")
    void testMissingParametersRejection() {
        String line = "ORDER|ORD-0001|2026-10-05 10:05:00|CL-06|MALNADAGR"; // only 5 tokens, needs 9
        ValidationResult res = inputValidationService.validateCommandLine(line);
        assertFalse(res.isValid());
        assertTrue(res.getRuleIdsString().contains(InputValidationService.INP_VAL_INVALID_PARAMS));
    }

    @Test
    @DisplayName("INP_VAL_004: Rejects malformed timestamp or date format")
    void testMalformedTimestampRejection() {
        String line = "ORDER|ORD-0001|INVALID-TIMESTAMP|CL-06|MALNADAGR|BUY|INTRADAY|1000|500.00";
        ValidationResult res = inputValidationService.validateCommandLine(line);
        assertFalse(res.isValid());
        assertTrue(res.getRuleIdsString().contains(InputValidationService.INP_VAL_INVALID_DATETIME));
    }

    @Test
    @DisplayName("INP_VAL_006: Rejects non-numeric quantity and non-numeric price")
    void testNonNumericTokensRejection() {
        String line = "ORDER|ORD-0001|2026-10-05 10:05:00|CL-06|MALNADAGR|BUY|INTRADAY|XYZ|ABC";
        ValidationResult res = inputValidationService.validateCommandLine(line);
        assertFalse(res.isValid());
        assertTrue(res.getRuleIdsString().contains(InputValidationService.INP_VAL_INVALID_NUMERIC));
    }

    @Test
    @DisplayName("INP_VAL_009 / EC-07: Pledge requested on a market holiday is rejected safely")
    void testPledgeOnHolidayRejectionEC07() {
        // Dussehra: 2026-10-20
        String line = "PLEDGE|PLG-005|2026-10-20 11:00:00|CL-04|COORGCOF|200";
        ValidationResult res = inputValidationService.validateCommandLine(line);
        assertFalse(res.isValid());
        assertTrue(res.getRuleIdsString().contains(InputValidationService.INP_VAL_MARKET_HOLIDAY));
    }
}
