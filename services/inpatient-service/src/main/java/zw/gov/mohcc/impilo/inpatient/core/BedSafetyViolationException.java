package zw.gov.mohcc.impilo.inpatient.core;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * Raised when a proposed bed assignment violates one or more clinical safety constraints
 * (gender bay, age group, isolation, oxygen, monitoring, ICU capability). 409 Conflict —
 * the request is well-formed but the bed is clinically unsafe for this patient.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class BedSafetyViolationException extends RuntimeException {
    private final transient List<String> violations;

    public BedSafetyViolationException(List<String> violations) {
        super("Bed assignment unsafe: " + String.join("; ", violations));
        this.violations = violations;
    }

    public List<String> getViolations() {
        return violations;
    }
}
