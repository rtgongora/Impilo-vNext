package zw.gov.mohcc.impilo.contract.envelope;

import java.util.List;

/**
 * Result of an event envelope validation.
 *
 * @param valid      true if the envelope passes all validation checks
 * @param violations list of validation violations (empty if valid)
 */
public record EnvelopeValidationResult(boolean valid, List<String> violations) {

    public EnvelopeValidationResult {
        violations = violations != null ? List.copyOf(violations) : List.of();
    }

    public static EnvelopeValidationResult passed() {
        return new EnvelopeValidationResult(true, List.of());
    }

    public static EnvelopeValidationResult invalid(List<String> violations) {
        return new EnvelopeValidationResult(false, violations);
    }

    public String summary() {
        if (valid) return "VALID";
        return "INVALID: " + String.join("; ", violations);
    }
}
