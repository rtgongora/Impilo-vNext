package zw.gov.mohcc.impilo.companion.context;

/**
 * Manifest v1.1 header constants.
 *
 * Mandatory headers on every request:
 *   X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID, Authorization
 *
 * Optional v1.1 headers:
 *   X-Client-Timeout-MS, Idempotency-Key
 *
 * Policy response headers (injected by gateway after OPA/TSHEPO allow):
 *   X-Policy-Decision, X-Policy-Version, X-Decision-Reason
 */
public final class CompanionHeaders {

    private CompanionHeaders() {
    }

    // ── Mandatory v1.1 Request Headers ──────────────────────────
    public static final String TENANT_ID      = "X-Tenant-ID";
    public static final String POD_ID         = "X-Pod-ID";
    public static final String REQUEST_ID     = "X-Request-ID";
    public static final String CORRELATION_ID = "X-Correlation-ID";

    // ── Authorization ───────────────────────────────────────────
    public static final String AUTHORIZATION  = "Authorization";
    public static final String BEARER_PREFIX  = "Bearer ";

    // ── Timeout Propagation ─────────────────────────────────────
    public static final String CLIENT_TIMEOUT_MS = "X-Client-Timeout-MS";

    // ── Idempotency ─────────────────────────────────────────────
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    // ── Policy Decision (gateway → service, injected after allow) ─
    public static final String POLICY_DECISION = "X-Policy-Decision";
    public static final String POLICY_VERSION  = "X-Policy-Version";
    public static final String DECISION_REASON = "X-Decision-Reason";

    // ── Hard-required headers (missing any of these => 400) ─────
    public static final String[] HARD_REQUIRED = {
            TENANT_ID, POD_ID
    };

    // ── All v1.1 context headers (auto-generated if absent: REQUEST_ID, CORRELATION_ID) ─
    public static final String[] CONTEXT_HEADERS = {
            TENANT_ID, POD_ID, REQUEST_ID, CORRELATION_ID
    };
}
