package zw.gov.mohcc.impilo.clinical.interpretation.model;

/**
 * Provenance class of the reference interval used for an interpretation, in precedence order.
 */
public enum RangeSource {
    /** Lab-delivered interval carried on the result — authoritative for labs. */
    LAB_DELIVERED,
    /** Governed FHIR ObservationDefinition.qualifiedInterval hosted in ZIBO (cited). */
    OBSERVATION_DEFINITION,
    /** No resolvable range — interpretation must be NO_REFERENCE_RANGE (never NORMAL). */
    NONE
}
