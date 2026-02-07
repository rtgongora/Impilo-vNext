package zw.gov.mohcc.impilo.shared.auth;

/**
 * Dual-mode API access classification.
 *
 * Every Impilo sovereign service operates in two modes:
 *   INTERNAL — platform service-to-service calls (mTLS, internal headers)
 *   EXTERNAL — third-party national service consumers (Tshepo-issued bearer token)
 */
public enum AccessMode {
    /** Platform-internal call (trusted, from another Impilo service via Envoy) */
    INTERNAL,
    /** External national service consumer (3rd-party app with Tshepo token) */
    EXTERNAL
}
