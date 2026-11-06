package edu.atria.capstone.model;

import java.util.Objects;

public class ValidationFailure {
    private final String ruleId;
    private final String message;

    public ValidationFailure(String ruleId, String message) {
        this.ruleId = ruleId;
        this.message = message;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidationFailure that)) return false;
        return Objects.equals(ruleId, that.ruleId) && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleId, message);
    }

    @Override
    public String toString() {
        return "[" + ruleId + "] " + message;
    }
}
