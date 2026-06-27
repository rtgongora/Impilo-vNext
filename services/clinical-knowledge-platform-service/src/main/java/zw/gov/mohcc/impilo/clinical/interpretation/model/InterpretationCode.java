package zw.gov.mohcc.impilo.clinical.interpretation.model;

/**
 * Classification of an observation against its reference interval, with the corresponding FHIR v3
 * ObservationInterpretation code (system
 * {@code http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation}).
 *
 * <p><strong>Doctrine:</strong> {@link #NO_REFERENCE_RANGE} is never reported as {@link #NORMAL};
 * an unconvertible unit yields {@link #UNIT_MISMATCH} (never a silent wrong comparison); an implausible
 * value yields {@link #DATA_QUALITY} (never "critical").</p>
 */
public enum InterpretationCode {
    NORMAL("N", false),
    LOW("L", true),
    HIGH("H", true),
    CRITICAL_LOW("LL", true),
    CRITICAL_HIGH("HH", true),
    /** No resolvable reference interval — explicitly NOT normal. */
    NO_REFERENCE_RANGE(null, false),
    /** Units not convertible between value and interval — fail-closed, no comparison performed. */
    UNIT_MISMATCH(null, false),
    /** Value outside plausible physiological bounds — a data-quality concern, not a clinical critical. */
    DATA_QUALITY(null, false);

    private final String fhirCode;
    private final boolean abnormal;

    InterpretationCode(String fhirCode, boolean abnormal) {
        this.fhirCode = fhirCode;
        this.abnormal = abnormal;
    }

    /** The FHIR v3 ObservationInterpretation code, or null when not applicable. */
    public String fhirCode() {
        return fhirCode;
    }

    /** True for LOW/HIGH/CRITICAL_* — values clinically outside the reference interval. */
    public boolean isAbnormal() {
        return abnormal;
    }

    public boolean isCritical() {
        return this == CRITICAL_LOW || this == CRITICAL_HIGH;
    }
}
