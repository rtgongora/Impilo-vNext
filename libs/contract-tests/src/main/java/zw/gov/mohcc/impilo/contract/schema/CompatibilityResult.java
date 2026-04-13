package zw.gov.mohcc.impilo.contract.schema;

import java.util.List;

/**
 * Result of a schema compatibility check.
 *
 * @param compatible true if the proposed schema is backward-compatible
 * @param violations list of compatibility violations (empty if compatible)
 */
public record CompatibilityResult(boolean compatible, List<String> violations) {

    public CompatibilityResult {
        violations = violations != null ? List.copyOf(violations) : List.of();
    }

    public static CompatibilityResult passed() {
        return new CompatibilityResult(true, List.of());
    }

    public static CompatibilityResult incompatible(List<String> violations) {
        return new CompatibilityResult(false, violations);
    }

    public String summary() {
        if (compatible) return "COMPATIBLE";
        return "INCOMPATIBLE: " + String.join("; ", violations);
    }
}
