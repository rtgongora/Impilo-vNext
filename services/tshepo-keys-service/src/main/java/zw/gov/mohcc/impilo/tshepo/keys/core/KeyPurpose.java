package zw.gov.mohcc.impilo.tshepo.keys.core;

/**
 * Canonical purposes a Tshepo signing key may be authorised for.
 *
 * <p>Key lookup is purpose-scoped and fail-closed: a key may only be used for the
 * purpose it was issued for. This is the foundation that lets later Tshepo trust
 * work (offline capability tokens, data-access permits, GDHCN document-signer
 * certificates and Verifiable Digital Health Certificate signing) bind to distinct
 * keys rather than sharing one general key.</p>
 */
public enum KeyPurpose {
    /** General-purpose signing (default for legacy/unscoped keys). */
    GENERAL,
    /** Step-up / high-assurance authentication artefacts. */
    STEP_UP,
    /** Offline capability tokens (tshepo-offline). */
    OFFLINE_CAPABILITY,
    /** Offline data packs — signed offline bundles (tshepo-offline). */
    OFFLINE_PACK,
    /** Data-access governance permit tokens. */
    PERMIT,
    /** GDHCN document signer certificates. */
    DOCUMENT_SIGNER,
    /** Future Verifiable Digital Health Certificate signing. */
    VDHC,
    /** VITO citizen QR tokens (pickup, wallet, emergency, health-id). */
    QR_SIGNING,
    /** SMART-card print-agent QR assertions (card credential signing). */
    CARD_ASSERTION,
    /** E-prescription version signing + prescription tokens (OROS, OF-B2 §13.2). */
    PRESCRIPTION_SIGNING,
    /** PDP decision envelopes — proof that Tshepo authorised a specific request (D2). */
    AUTHZ_DECISION;

    /**
     * Resolve a purpose name. Null or blank means the caller expressed no purpose and gets
     * {@link #GENERAL}, the documented default.
     *
     * <p>An unrecognised name throws. It used to fall back to {@code GENERAL}, which meant a
     * caller asking for a purpose this deployment does not know about was quietly handed a
     * general key and signed a trust-bearing artefact with it — the exact substitution the
     * purpose scoping exists to prevent, and invisible to the caller because the response
     * looks identical. During a rolling upgrade that is precisely the caller worth refusing.</p>
     */
    public static KeyPurpose fromString(String value) {
        if (value == null || value.isBlank()) {
            return GENERAL;
        }
        try {
            return KeyPurpose.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown key purpose: " + value);
        }
    }
}
