package zw.gov.mohcc.impilo.tshepo.contracts.headers;

/**
 * SINGLE SOURCE OF TRUTH for trust headers flowing through Envoy → TSHEPO → downstream.
 *
 * <p>Aligned with Health OS Manifest v1.2 and Multi-Class Identifier Doctrine
 * (see docs/doctrine/health-os-doctrine.md §8, §11).</p>
 *
 * <h3>Request headers — mandatory (UI/service → Envoy → TSHEPO):</h3>
 * <ul>
 *     <li>{@code x-tenant-id} — multi-tenant isolation</li>
 *     <li>{@code x-actor-id} — Health ID / person anchor (mandatory)</li>
 *     <li>{@code x-actor-type} — PROVIDER, ADMIN, CITIZEN, SYSTEM, SERVICE, CAREGIVER</li>
 *     <li>{@code x-purpose-of-use} — why this request is being made</li>
 *     <li>{@code x-device-fingerprint} — device identity hash</li>
 *     <li>{@code x-correlation-id} — request trace</li>
 * </ul>
 *
 * <h3>Request headers — actor identity (Health OS §5–§6):</h3>
 * <ul>
 *     <li>{@code x-provider-id} — regulated professional role ID (VARAPI-issued)</li>
 * </ul>
 *
 * <h3>Request headers — operational context (Health OS §7):</h3>
 * <ul>
 *     <li>{@code x-facility-id}, {@code x-department-id}, {@code x-ward-id},
 *         {@code x-workspace-id}, {@code x-programme-id}, {@code x-shift-id}</li>
 * </ul>
 *
 * <h3>Request headers — governance (Health OS §11):</h3>
 * <ul>
 *     <li>{@code x-assurance-level} — LOA1–LOA4 identity strength</li>
 *     <li>{@code x-subject-id} — patient/subject of action</li>
 *     <li>{@code x-access-mode} — INTERNAL or EXTERNAL</li>
 * </ul>
 *
 * <h3>Response/obligation headers (TSHEPO → Envoy → downstream):</h3>
 * <ul>
 *     <li>{@code x-decision} — ALLOW, DENY, STEP_UP_REQUIRED</li>
 *     <li>{@code x-obligations} — JSON obligations blob</li>
 *     <li>{@code x-max-scope} — maximum data scope for this request</li>
 *     <li>{@code x-mask-fields} — comma-separated fields to mask</li>
 *     <li>{@code x-logging-level} — audit logging level override</li>
 * </ul>
 *
 * These must match across:
 * <ol>
 *     <li>{@code tshepo-contracts} (this class)</li>
 *     <li>{@code CompanionHeaders.java} (tech-companion lib)</li>
 *     <li>{@code contracts/health-os-identifiers.ts} (TypeScript)</li>
 *     <li>{@code infra/envoy/envoy-runtime.yaml} (Envoy config)</li>
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

    // ── Actor identity (Health OS §5–§6) ─────────────────────────────────
    public static final String PROVIDER_ID = "x-provider-id";

    // ── Operational context (Health OS §7) ────────────────────────────────
    public static final String FACILITY_ID   = "x-facility-id";
    public static final String DEPARTMENT_ID = "x-department-id";
    public static final String WARD_ID       = "x-ward-id";
    public static final String WORKSPACE_ID  = "x-workspace-id";
    public static final String PROGRAMME_ID  = "x-programme-id";
    public static final String SHIFT_ID      = "x-shift-id";

    // ── Governance (Health OS §11) ────────────────────────────────────────
    public static final String ASSURANCE_LEVEL = "x-assurance-level";
    public static final String SUBJECT_ID      = "x-subject-id";
    public static final String ACCESS_MODE     = "x-access-mode";
    public static final String WORKFLOW_STATE  = "x-workflow-state";

    // ── Response / obligation headers ────────────────────────────────────
    public static final String DECISION      = "x-decision";
    public static final String OBLIGATIONS   = "x-obligations";
    public static final String MAX_SCOPE     = "x-max-scope";
    public static final String MASK_FIELDS   = "x-mask-fields";
    public static final String LOGGING_LEVEL = "x-logging-level";

    // ── Internal service-to-service headers ──────────────────────────────
    public static final String SERVICE_ID    = "x-service-id";
    public static final String MTLS_IDENTITY = "x-mtls-identity";

    /** Mandatory request headers that must be present on every inbound request. */
    public static final String[] MANDATORY = {
            TENANT_ID, ACTOR_ID, ACTOR_TYPE,
            PURPOSE_OF_USE, DEVICE_FINGERPRINT, CORRELATION_ID
    };
}
