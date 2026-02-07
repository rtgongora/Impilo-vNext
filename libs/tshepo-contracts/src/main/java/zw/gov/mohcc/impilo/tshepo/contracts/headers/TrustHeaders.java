package zw.gov.mohcc.impilo.tshepo.contracts.headers;

/**
 * SINGLE SOURCE OF TRUTH for the 14 trust headers flowing through Envoy → TSHEPO → downstream.
 *
 * <h3>Request headers (UI/service → Envoy → TSHEPO):</h3>
 * <ul>
 *     <li>{@code X-Tenant-Id} — multi-tenant isolation (mandatory)</li>
 *     <li>{@code X-Actor-Id} — authenticated principal (mandatory)</li>
 *     <li>{@code X-Actor-Type} — PROVIDER, ADMIN, CITIZEN, SYSTEM, SERVICE (mandatory)</li>
 *     <li>{@code X-Purpose-Of-Use} — why this request is being made (mandatory)</li>
 *     <li>{@code X-Device-Fingerprint} — device identity hash (mandatory)</li>
 *     <li>{@code X-Correlation-Id} — request trace (mandatory)</li>
 *     <li>{@code X-Facility-Id} — facility scope (contextual)</li>
 *     <li>{@code X-Workspace-Id} — workspace scope (contextual)</li>
 *     <li>{@code X-Shift-Id} — duty shift scope (contextual)</li>
 * </ul>
 *
 * <h3>Response/obligation headers (TSHEPO → Envoy → downstream):</h3>
 * <ul>
 *     <li>{@code X-Decision} — ALLOW, DENY, STEP_UP_REQUIRED</li>
 *     <li>{@code X-Obligations} — JSON obligations blob</li>
 *     <li>{@code X-Max-Scope} — maximum data scope for this request</li>
 *     <li>{@code X-Mask-Fields} — comma-separated fields to mask</li>
 *     <li>{@code X-Logging-Level} — audit logging level override</li>
 * </ul>
 *
 * These must match across:
 * <ol>
 *     <li>{@code tshepo-contracts} (this class)</li>
 *     <li>{@code shared-ui/lib/contracts.ts} (TypeScript)</li>
 *     <li>{@code infra/envoy/envoy.yaml} (Envoy config)</li>
 * </ol>
 */
public final class TrustHeaders {

    private TrustHeaders() {}

    // ── Request headers (mandatory) ──────────────────────────────────────
    public static final String TENANT_ID          = "x-tenant-id";
    public static final String ACTOR_ID           = "x-actor-id";
    public static final String ACTOR_TYPE         = "x-actor-type";
    public static final String PURPOSE_OF_USE     = "x-purpose-of-use";
    public static final String DEVICE_FINGERPRINT = "x-device-fingerprint";
    public static final String CORRELATION_ID     = "x-correlation-id";

    // ── Request headers (contextual) ─────────────────────────────────────
    public static final String FACILITY_ID  = "x-facility-id";
    public static final String WORKSPACE_ID = "x-workspace-id";
    public static final String SHIFT_ID     = "x-shift-id";

    // ── Response / obligation headers ────────────────────────────────────
    public static final String DECISION      = "x-decision";
    public static final String OBLIGATIONS   = "x-obligations";
    public static final String MAX_SCOPE     = "x-max-scope";
    public static final String MASK_FIELDS   = "x-mask-fields";
    public static final String LOGGING_LEVEL = "x-logging-level";

    // ── Internal service-to-service headers ──────────────────────────────
    public static final String ACCESS_MODE   = "x-access-mode";
    public static final String SERVICE_ID    = "x-service-id";
    public static final String MTLS_IDENTITY = "x-mtls-identity";

    /** Mandatory request headers that must be present on every inbound request. */
    public static final String[] MANDATORY = {
            TENANT_ID, ACTOR_ID, ACTOR_TYPE,
            PURPOSE_OF_USE, DEVICE_FINGERPRINT, CORRELATION_ID
    };
}
