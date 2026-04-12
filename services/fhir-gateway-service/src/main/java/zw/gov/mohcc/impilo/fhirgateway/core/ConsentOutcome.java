package zw.gov.mohcc.impilo.fhirgateway.core;

/**
 * Result of a consent evaluation at the FHIR Gateway boundary.
 *
 * <p>Maps the tshepo-consent-service response into a simple three-state
 * decision that the gateway forward path can act on.</p>
 */
public enum ConsentOutcome {

    /** Active consent permits the requested access. */
    PERMIT,

    /** Consent explicitly denies the requested access, or no active consent exists. */
    DENY,

    /** Consent evaluation is not applicable (e.g. public-health resource types). */
    NOT_APPLICABLE,

    /** Consent was bypassed via break-glass; enhanced audit required. */
    BREAK_GLASS_BYPASS
}
