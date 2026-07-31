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
 *     <li>{@code x-visibility-tier} — coarse visibility tier (see {@code DataVisibilityTier})</li>
 *     <li>{@code x-pii-access} — {@code NONE|MASKED|LIMITED|FULL}</li>
 *     <li>{@code x-clinical-access} — {@code NONE|SUMMARY|FULL}</li>
 *     <li>{@code x-aggregate-only} — {@code true} / {@code false}</li>
 *     <li>{@code x-resource-sensitivity} — {@code DataSensitivityClass} name</li>
 *     <li>{@code x-escalation-grant-id} — active workflow escalation grant UUID</li>
 *     <li>{@code x-export-policy} — {@code PROHIBITED|AGGREGATE_ONLY|REDACTED|FULL_AUDITED}</li>
 *     <li>{@code x-suppress-fields} — comma-separated JSON field paths to omit</li>
 *     <li>{@code x-drill-down-allowed} — {@code true} / {@code false}</li>
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
    /** Canonical TUSO numeric facility identifier (Long) for assignment-aware policy. */
    public static final String TUSO_FACILITY_ID = "x-tuso-facility-id";
    public static final String DEPARTMENT_ID = "x-department-id";
    public static final String WARD_ID       = "x-ward-id";
    public static final String WORKSPACE_ID  = "x-workspace-id";
    public static final String PROGRAMME_ID  = "x-programme-id";
    public static final String SHIFT_ID      = "x-shift-id";
    /** Signed, revocable WORK_CONTEXT duty token — the PDP's authoritative operational context. */
    public static final String WORK_CONTEXT_TOKEN = "x-work-context-token";

    // ── Governance (Health OS §11) ────────────────────────────────────────
    public static final String ASSURANCE_LEVEL = "x-assurance-level";
    // Authentication assurance is derived from the validated token at TSHEPO/edge.
    // Clients must never be trusted to supply these values.
    public static final String AUTHENTICATION_AAL = "x-authentication-aal";
    public static final String AUTHENTICATION_AMR = "x-authentication-amr";
    public static final String AUTHENTICATION_TIME = "x-authentication-time";
    public static final String AUTHENTICATION_STEP_UP_TIME = "x-authentication-step-up-time";
    public static final String AUTHENTICATION_PHISHING_RESISTANT = "x-authentication-phishing-resistant";
    public static final String AUTHENTICATION_SESSION_ID = "x-authentication-session-id";
    public static final String AUTHENTICATION_FLOW_ID = "x-authentication-flow-id";
    public static final String SUBJECT_ID      = "x-subject-id";
    public static final String ACCESS_MODE     = "x-access-mode";
    public static final String WORKFLOW_STATE  = "x-workflow-state";

    // ── Response / obligation headers ────────────────────────────────────
    public static final String DECISION      = "x-decision";
    public static final String OBLIGATIONS   = "x-obligations";
    public static final String MAX_SCOPE     = "x-max-scope";
    public static final String MASK_FIELDS   = "x-mask-fields";
    public static final String LOGGING_LEVEL = "x-logging-level";

    /**
     * Signed decision envelope (Phase D, D2): a short-lived compact JWS minted by the PDP
     * on ALLOW and bound to the request it authorised. The other response headers state
     * the decision; this one <em>proves</em> the PDP made it, so a caller that reached a
     * service without traversing the gate cannot fabricate one.
     */
    public static final String DECISION_ENVELOPE = "x-decision-envelope";

    public static final String VISIBILITY_TIER        = "x-visibility-tier";
    public static final String PII_ACCESS             = "x-pii-access";
    public static final String CLINICAL_ACCESS      = "x-clinical-access";
    public static final String AGGREGATE_ONLY       = "x-aggregate-only";
    public static final String RESOURCE_SENSITIVITY = "x-resource-sensitivity";
    public static final String ESCALATION_GRANT_ID  = "x-escalation-grant-id";
    public static final String EXPORT_POLICY        = "x-export-policy";
    public static final String SUPPRESS_FIELDS      = "x-suppress-fields";
    public static final String DRILL_DOWN_ALLOWED   = "x-drill-down-allowed";
    /**
     * Confidential categories of {@code SPECIALLY_PROTECTED} content this requester may receive
     * (comma-separated; {@code *} means all). Absent means none — protected content is withheld.
     */
    public static final String CONFIDENTIAL_CATEGORIES = "x-confidential-categories";

    // ── Internal service-to-service headers ──────────────────────────────
    public static final String SERVICE_ID    = "x-service-id";
    public static final String MTLS_IDENTITY = "x-mtls-identity";

    /** Mandatory request headers that must be present on every inbound request. */
    public static final String[] MANDATORY = {
            TENANT_ID, ACTOR_ID, ACTOR_TYPE,
            PURPOSE_OF_USE, DEVICE_FINGERPRINT, CORRELATION_ID
    };
}
