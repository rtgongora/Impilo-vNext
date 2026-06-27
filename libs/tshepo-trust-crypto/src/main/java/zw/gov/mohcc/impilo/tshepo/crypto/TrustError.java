package zw.gov.mohcc.impilo.tshepo.crypto;

/**
 * Canonical trust-verification error model for the Tshepo trust plane. Every consumer
 * (offline capability tokens, data-access permits, GDHCN document-signer verification)
 * maps a failure to one of these, so error handling and audit are uniform across the
 * plane instead of each verifier inventing its own string reasons.
 */
public enum TrustError {
    /** Token could not be parsed as a compact JWS. */
    MALFORMED,
    /** The JWS algorithm is not on the configured allowlist. */
    UNSUPPORTED_ALG,
    /** No key matching the token's {@code kid} is present in the trusted key set. */
    UNKNOWN_KEY,
    /** The signature did not verify against the resolved key. */
    INVALID_SIGNATURE,
    /** The token's {@code exp} is in the past (allowing for clock skew). */
    EXPIRED,
    /** The token's {@code nbf} is in the future (allowing for clock skew). */
    NOT_YET_VALID,
    /** The token's {@code iss} is not a trusted issuer. */
    UNKNOWN_ISSUER,
    /** The token's {@code aud} does not include the required audience. */
    WRONG_AUDIENCE,
    /** The token's purpose claim does not match the required purpose. */
    WRONG_PURPOSE,
    /** The signing key / certificate has been revoked (consumer-supplied check). */
    REVOKED,
    /** The token crosses a trust boundary it is not authorised for (consumer-supplied check). */
    TRUST_BOUNDARY
}
