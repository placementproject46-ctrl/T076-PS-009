package edu.atria.capstone.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ValidationResult {
    private final List<ValidationFailure> failures = new ArrayList<>();

    public static ValidationResult success() {
        return new ValidationResult();
    }

    public static ValidationResult failure(String ruleId, String message) {
        ValidationResult vr = new ValidationResult();
        vr.addFailure(ruleId, message);
        return vr;
    }

    public void addFailure(String ruleId, String message) {
        failures.add(new ValidationFailure(ruleId, message));
    }

    public boolean isValid() {
        return failures.isEmpty();
    }

    public List<ValidationFailure> getFailures() {
        return Collections.unmodifiableList(failures);
    }

    public String getRuleIdsString() {
        if (failures.isEmpty()) {
            return "";
        }
        return failures.stream()
                .map(ValidationFailure::getRuleId)
                .collect(Collectors.joining(";"));
    }

    public String getCombinedMessage() {
        if (failures.isEmpty()) {
            return "SUCCESS";
        }
        return failures.stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.joining(" | "));
    }

    @Override
    public String toString() {
        return isValid() ? "VALID" : "INVALID: " + getRuleIdsString() + " (" + getCombinedMessage() + ")";
    }
}
