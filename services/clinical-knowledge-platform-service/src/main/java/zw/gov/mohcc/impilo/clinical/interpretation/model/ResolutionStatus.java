package zw.gov.mohcc.impilo.clinical.interpretation.model;

/**
 * Outcome of resolving a reference interval for an observation.
 */
public enum ResolutionStatus {
    /** A usable, unit-comparable interval was found. */
    RESOLVED,
    /** No applicable interval — interpretation is NO_REFERENCE_RANGE (never NORMAL). */
    NO_REFERENCE_RANGE,
    /** An interval exists but its units are not convertible to the value's units (fail-closed). */
    UNIT_MISMATCH
}
