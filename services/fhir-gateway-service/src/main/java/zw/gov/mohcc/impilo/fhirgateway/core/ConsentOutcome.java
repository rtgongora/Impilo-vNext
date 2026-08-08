package zw.gov.mohcc.impilo.fhirgateway.core;

/**
 * Result of a consent evaluation at the FHIR Gateway boundary.
 *
 * <p>Maps the tshepo-consent-service response into a decision the gateway forward path can act on.
 * Only {@link #DENY} refuses the forward; every other value permits it, and the value recorded on
 * the audit row says on what basis.</p>
 */
public enum ConsentOutcome {

    /** An active directive permits the requested access. */
    PERMIT,

    /**
     * Consent refuses the requested access, the consent service could not be reached, or a read
     * was attempted with no directive permitting it. The forward does not happen.
     */
    DENY,

    /** Consent evaluation is not applicable (e.g. public-health resource types, no subject CPID). */
    NOT_APPLICABLE,

    /** Consent was bypassed via break-glass; enhanced audit required. */
    BREAK_GLASS_BYPASS,

    /**
     * A WRITE proceeded because the subject has no directive on the point — not because one
     * permits it.
     *
     * <p>Absence of a directive is not refusal. Recording care that has already happened, so that
     * it survives outside the facility that delivered it, is not the moment to adjudicate consent;
     * consent governs who may later READ the record, and reads remain fail-closed. Refusing the
     * write instead loses the fact entirely, silently, and forever — measured 2026-08-08, this is
     * what had happened: 22 of 22 non-break-glass forwards in the gateway's whole history were
     * denied, 20 of them monitoring observations, and the only successes were break-glass.</p>
     *
     * <p>Distinct from {@link #PERMIT} on purpose. A regulator asking "on whose authority was this
     * recorded" must be able to tell a directive that said yes from the absence of one, and the
     * audit row is the only place that answer survives.</p>
     */
    PERMIT_NO_DIRECTIVE
}
