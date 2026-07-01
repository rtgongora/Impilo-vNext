package zw.gov.mohcc.impilo.costa.domain.enums;

/** Outcome of a budget availability control check. Clinical emergencies never HARD_STOP. */
public enum AvailabilityDecision {
    ALLOW,
    WARN,
    HARD_STOP,
    EMERGENCY_OVERRIDE
}
