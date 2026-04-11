package zw.gov.mohcc.impilo.companion.context;

/**
 * Health OS Manifest v1.2 header constants.
 *
 * <p>Aligned with the Health OS Multi-Class Identifier Doctrine
 * (see docs/doctrine/health-os-doctrine.md §8).</p>
 *
 * <h3>Mandatory headers on every request</h3>
 * X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID, Authorization
 *
 * <h3>Actor identity headers (who)</h3>
 * X-Actor-ID (Health ID — person anchor), X-Actor-Type, X-Provider-ID (regulated role ID)
 *
 * <h3>Context headers (where / under what setting)</h3>
 * X-Facility-ID, X-Department-ID, X-Ward-ID, X-Workspace-ID, X-Programme-ID, X-Shift-ID
 *
 * <h3>Governance headers (why / under what authority)</h3>
 * X-Purpose-Of-Use, X-Assurance-Level, X-Subject-ID, X-Access-Mode
 *
 * <h3>Idempotency and timeout</h3>
 * Idempotency-Key, X-Client-Timeout-MS
 *
 * <h3>Policy response headers (injected by gateway after OPA/TSHEPO allow)</h3>
 * X-Policy-Decision, X-Policy-Version, X-Decision-Reason
 */
public final class CompanionHeaders {

    private CompanionHeaders() {
    }

    // ── Mandatory v1.2 Request Headers ──────────────────────────
    public static final String TENANT_ID      = "X-Tenant-ID";
    public static final String POD_ID         = "X-Pod-ID";
    public static final String REQUEST_ID     = "X-Request-ID";
    public static final String CORRELATION_ID = "X-Correlation-ID";

    // ── Authorization ───────────────────────────────────────────
    public static final String AUTHORIZATION  = "Authorization";
    public static final String BEARER_PREFIX  = "Bearer ";

    // ── Actor Identity (Health OS §5–§6: who is acting) ─────────
    public static final String ACTOR_ID       = "X-Actor-ID";       // Health ID — person anchor
    public static final String ACTOR_TYPE     = "X-Actor-Type";     // PROVIDER, OPERATOR, CITIZEN, SYSTEM, CAREGIVER
    public static final String PROVIDER_ID    = "X-Provider-ID";    // Regulated professional role ID (VARAPI)

    // ── Operational Context (Health OS §7: where / under what) ──
    public static final String FACILITY_ID    = "X-Facility-ID";
    public static final String DEPARTMENT_ID  = "X-Department-ID";
    public static final String WARD_ID        = "X-Ward-ID";
    public static final String WORKSPACE_ID   = "X-Workspace-ID";
    public static final String PROGRAMME_ID   = "X-Programme-ID";
    public static final String SHIFT_ID       = "X-Shift-ID";

    // ── Governance (Health OS §11: why / under what authority) ───
    public static final String PURPOSE_OF_USE   = "X-Purpose-Of-Use";
    public static final String ASSURANCE_LEVEL  = "X-Assurance-Level";  // LOA1–LOA4
    public static final String SUBJECT_ID       = "X-Subject-ID";       // Patient/subject of action
    public static final String ACCESS_MODE      = "X-Access-Mode";      // INTERNAL, EXTERNAL

    // ── Timeout & Idempotency ───────────────────────────────────
    public static final String CLIENT_TIMEOUT_MS = "X-Client-Timeout-MS";
    public static final String IDEMPOTENCY_KEY   = "Idempotency-Key";

    // ── Policy Decision (gateway → service, injected after allow) ─
    public static final String POLICY_DECISION = "X-Policy-Decision";
    public static final String POLICY_VERSION  = "X-Policy-Version";
    public static final String DECISION_REASON = "X-Decision-Reason";

    // ── Hard-required headers (missing any of these => 400) ─────
    // Manifest v1.2 spec: ALL FOUR must be present on every v1.2 request.
    public static final String[] HARD_REQUIRED = {
            TENANT_ID, POD_ID, REQUEST_ID, CORRELATION_ID
    };
}
