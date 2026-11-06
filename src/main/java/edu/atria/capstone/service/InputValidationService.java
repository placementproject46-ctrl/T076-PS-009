package edu.atria.capstone.service;

import edu.atria.capstone.model.PledgeGroup;
import edu.atria.capstone.model.ValidationFailure;
import edu.atria.capstone.model.ValidationResult;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.HolidayRepository;
import edu.atria.capstone.repository.ScripRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;

public class InputValidationService {
    public static final String INP_VAL_CSV_MALFORMED = "INP_VAL_001";
    public static final String INP_VAL_DUPLICATE_CLIENT = "INP_VAL_002";
    public static final String INP_VAL_DUPLICATE_SCRIP = "INP_VAL_003";
    public static final String INP_VAL_INVALID_DATETIME = "INP_VAL_004";
    public static final String INP_VAL_INVALID_GROUP = "INP_VAL_005";
    public static final String INP_VAL_INVALID_NUMERIC = "INP_VAL_006";
    public static final String INP_VAL_UNKNOWN_COMMAND = "INP_VAL_007";
    public static final String INP_VAL_INVALID_PARAMS = "INP_VAL_008";
    public static final String INP_VAL_MARKET_HOLIDAY = "INP_VAL_009";
    public static final String INP_VAL_EMPTY_FILE = "INP_VAL_010";

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Set<String> VALID_COMMANDS = Set.of(
            "ORDER", "PLEDGE", "UNPLEDGE", "FUNDS_IN", "RETAG",
            "PRICE_UPDATE", "SETTLE", "INTRADAY_SQUARE_OFF", "EOD", "PRINT_REPORT"
    );

    private final ClientRepository clientRepository;
    private final ScripRepository scripRepository;
    private final HolidayRepository holidayRepository;

    public InputValidationService(ClientRepository clientRepository,
                                  ScripRepository scripRepository,
                                  HolidayRepository holidayRepository) {
        this.clientRepository = clientRepository;
        this.scripRepository = scripRepository;
        this.holidayRepository = holidayRepository;
    }

    /**
     * Validates a command line syntax before execution.
     */
    public ValidationResult validateCommandLine(String line) {
        ValidationResult result = new ValidationResult();
        if (line == null || line.trim().isEmpty()) {
            return result; // Empty lines ignored safely
        }

        String trimmed = line.trim();
        if (trimmed.startsWith("#")) {
            return result; // Comment ignored
        }

        String[] tokens = trimmed.split("\\|", -1);
        String cmd = tokens[0].trim().toUpperCase();

        if (!VALID_COMMANDS.contains(cmd)) {
            result.addFailure(INP_VAL_UNKNOWN_COMMAND, "Unrecognized command type: " + cmd);
            return result;
        }

        switch (cmd) {
            case "ORDER" -> validateOrderTokens(tokens, result);
            case "PLEDGE" -> validatePledgeTokens(tokens, result);
            case "UNPLEDGE" -> validateUnpledgeTokens(tokens, result);
            case "FUNDS_IN" -> validateFundsInTokens(tokens, result);
            case "RETAG" -> validateRetagTokens(tokens, result);
            case "PRICE_UPDATE" -> validatePriceUpdateTokens(tokens, result);
            case "SETTLE" -> validateDateToken(tokens, 1, "SETTLE", result);
            case "INTRADAY_SQUARE_OFF" -> validateTimestampToken(tokens, 1, "INTRADAY_SQUARE_OFF", result);
            case "EOD" -> validateDateToken(tokens, 1, "EOD", result);
            case "PRINT_REPORT" -> validatePrintReportTokens(tokens, result);
        }

        return result;
    }

    private void validateOrderTokens(String[] tokens, ValidationResult result) {
        // ORDER|order_id|timestamp|client_id|symbol|side|product|quantity|price
        if (tokens.length < 9) {
            result.addFailure(INP_VAL_INVALID_PARAMS, "ORDER requires 9 parameters. Received: " + tokens.length);
            return;
        }
        validateTimestamp(tokens[2].trim(), result);
        validatePositiveInteger(tokens[7].trim(), "Quantity", result);
        validatePositiveDecimal(tokens[8].trim(), "Price", result);
    }

    private void validatePledgeTokens(String[] tokens, ValidationResult result) {
        // PLEDGE|request_id|timestamp|client_id|symbol|quantity
        if (tokens.length < 6) {
            result.addFailure(INP_VAL_INVALID_PARAMS, "PLEDGE requires 6 parameters. Received: " + tokens.length);
            return;
        }
        LocalDateTime ts = validateTimestamp(tokens[2].trim(), result);
        if (ts != null && holidayRepository != null && holidayRepository.isNonTradingDay(ts.toLocalDate())) {
            result.addFailure(INP_VAL_MARKET_HOLIDAY,
                    "Pledge request rejected on market holiday/weekend: " + ts.toLocalDate());
        }
        validatePositiveInteger(tokens[5].trim(), "Pledge quantity", result);
    }

    private void validateUnpledgeTokens(String[] tokens, ValidationResult result) {
        // UNPLEDGE|request_id|timestamp|client_id|symbol|quantity
        if (tokens.length < 6) {
            result.addFailure(INP_VAL_INVALID_PARAMS, "UNPLEDGE requires 6 parameters. Received: " + tokens.length);
            return;
        }
        validateTimestamp(tokens[2].trim(), result);
        validatePositiveInteger(tokens[5].trim(), "Unpledge quantity", result);
    }

    private void validateFundsInTokens(String[] tokens, ValidationResult result) {
        // FUNDS_IN|request_id|timestamp|client_id|amount
        if (tokens.length < 5) {
            result.addFailure(INP_VAL_INVALID_PARAMS, "FUNDS_IN requires 5 parameters. Received: " + tokens.length);
            return;
        }
        validateTimestamp(tokens[2].trim(), result);
        validatePositiveDecimal(tokens[4].trim(), "Funds amount", result);
    }

    private void validateRetagTokens(String[] tokens, ValidationResult result) {
        // RETAG|timestamp|symbol|new_group
        if (tokens.length < 4) {
            result.addFailure(INP_VAL_INVALID_PARAMS, "RETAG requires 4 parameters. Received: " + tokens.length);
            return;
        }
        validateTimestamp(tokens[1].trim(), result);
        try {
            PledgeGroup.fromString(tokens[3].trim());
        } catch (Exception e) {
            result.addFailure(INP_VAL_INVALID_GROUP, "Invalid pledge group: " + tokens[3]);
        }
    }

    private void validatePriceUpdateTokens(String[] tokens, ValidationResult result) {
        // PRICE_UPDATE|timestamp|symbol|price
        if (tokens.length < 4) {
            result.addFailure(INP_VAL_INVALID_PARAMS, "PRICE_UPDATE requires 4 parameters. Received: " + tokens.length);
            return;
        }
        validateTimestamp(tokens[1].trim(), result);
        validatePositiveDecimal(tokens[3].trim(), "Update price", result);
    }

    private void validatePrintReportTokens(String[] tokens, ValidationResult result) {
        // PRINT_REPORT|client_id|date
        if (tokens.length < 3) {
            result.addFailure(INP_VAL_INVALID_PARAMS, "PRINT_REPORT requires 3 parameters. Received: " + tokens.length);
            return;
        }
        validateDateToken(tokens, 2, "PRINT_REPORT", result);
    }

    private void validateDateToken(String[] tokens, int idx, String cmdName, ValidationResult result) {
        if (tokens.length <= idx) {
            result.addFailure(INP_VAL_INVALID_PARAMS, cmdName + " missing date argument.");
            return;
        }
        try {
            LocalDate.parse(tokens[idx].trim());
        } catch (DateTimeParseException e) {
            result.addFailure(INP_VAL_INVALID_DATETIME, "Invalid date format in " + cmdName + ": " + tokens[idx]);
        }
    }

    private void validateTimestampToken(String[] tokens, int idx, String cmdName, ValidationResult result) {
        if (tokens.length <= idx) {
            result.addFailure(INP_VAL_INVALID_PARAMS, cmdName + " missing timestamp argument.");
            return;
        }
        validateTimestamp(tokens[idx].trim(), result);
    }

    private LocalDateTime validateTimestamp(String text, ValidationResult result) {
        try {
            return LocalDateTime.parse(text, TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException e) {
            result.addFailure(INP_VAL_INVALID_DATETIME, "Invalid timestamp format (expected yyyy-MM-dd HH:mm:ss): " + text);
            return null;
        }
    }

    private void validatePositiveInteger(String text, String fieldName, ValidationResult result) {
        try {
            int val = Integer.parseInt(text);
            if (val <= 0) {
                result.addFailure(INP_VAL_INVALID_NUMERIC, fieldName + " must be positive: " + val);
            }
        } catch (NumberFormatException e) {
            result.addFailure(INP_VAL_INVALID_NUMERIC, fieldName + " must be a valid integer: " + text);
        }
    }

    private void validatePositiveDecimal(String text, String fieldName, ValidationResult result) {
        try {
            BigDecimal val = new BigDecimal(text);
            if (val.compareTo(BigDecimal.ZERO) <= 0) {
                result.addFailure(INP_VAL_INVALID_NUMERIC, fieldName + " must be greater than zero: " + val);
            }
        } catch (Exception e) {
            result.addFailure(INP_VAL_INVALID_NUMERIC, fieldName + " must be a valid decimal: " + text);
        }
    }
}
