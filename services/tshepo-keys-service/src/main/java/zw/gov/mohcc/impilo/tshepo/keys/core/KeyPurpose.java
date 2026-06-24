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
    /** Data-access governance permit tokens. */
    PERMIT,
    /** GDHCN document signer certificates. */
    DOCUMENT_SIGNER,
    /** Future Verifiable Digital Health Certificate signing. */
    VDHC;

    public static KeyPurpose fromString(String value) {
        if (value == null || value.isBlank()) {
            return GENERAL;
        }
        try {
            return KeyPurpose.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return GENERAL;
        }
    }
}
