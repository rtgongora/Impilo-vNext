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
 * X-Purpose-Of-Use, X-Device-Fingerprint, X-Assurance-Level, X-Subject-ID, X-Access-Mode
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
    public static final String TUSO_FACILITY_ID = "X-Tuso-Facility-Id";
    public static final String DEPARTMENT_ID  = "X-Department-ID";
    public static final String WARD_ID        = "X-Ward-ID";
    public static final String WORKSPACE_ID   = "X-Workspace-ID";
    public static final String PROGRAMME_ID   = "X-Programme-ID";
    public static final String SHIFT_ID       = "X-Shift-ID";

    // ── Governance (Health OS §11: why / under what authority) ───
    public static final String PURPOSE_OF_USE   = "X-Purpose-Of-Use";
    /** Device / client instance binding for risk and audit (recommended on all calls). */
    public static final String DEVICE_FINGERPRINT = "X-Device-Fingerprint";
    public static final String ASSURANCE_LEVEL  = "X-Assurance-Level";  // LOA1–LOA4
    public static final String SUBJECT_ID       = "X-Subject-ID";       // Patient/subject of action
    public static final String ACCESS_MODE      = "X-Access-Mode";      // INTERNAL, EXTERNAL
    public static final String WORKFLOW_STATE   = "X-Workflow-State";   // e.g. DRAFT, ACTIVE, DISCHARGED

    // ── Service identity (Health OS Extensibility Doctrine §4 / §9) ──────
    /** Stable registered identifier of the calling sovereign service (e.g. "experience-bff", "msika-apps-service"). */
    public static final String SERVICE_ID      = "X-Service-Id";
    /** Human-readable name of the calling service. */
    public static final String SERVICE_NAME    = "X-Service-Name";
    /** Semantic version of the calling service. */
    public static final String SERVICE_VERSION = "X-Service-Version";
    /** Classification of the request originator: HUMAN, SYSTEM, SCHEDULED_JOB, BACKGROUND_WORKER, EVENT_CONSUMER, AI_ASSISTED, EXTERNAL_APP. */
    public static final String REQUEST_SOURCE  = "X-Request-Source";

    // ── External application origination (Doctrine §6 / §9.2) ────────────
    /** Registered external application id, when the request originates from an approved external app. */
    public static final String EXTERNAL_APP_ID     = "X-External-App-Id";
    /** Integration category of the external app (mirrors `IntegrationCategory` enum). */
    public static final String INTEGRATION_TYPE    = "X-Integration-Type";
    /** Version of the integration contract under which the call is being made. */
    public static final String INTEGRATION_VERSION = "X-Integration-Version";
    /** Cryptographic signature of the request body (HMAC-SHA256 or ED25519) for external app requests. */
    public static final String REQUEST_SIGNATURE   = "X-Request-Signature";

    // ── AI skill origination (when REQUEST_SOURCE = AI_ASSISTED) ─────────
    /** Identifier of the activated AI Skill manifest invoking this action. */
    public static final String AI_SKILL_ID   = "X-AI-Skill-Id";
    /** AI model reference used (from ai-model-registry-service). */
    public static final String AI_MODEL_REF  = "X-AI-Model-Ref";

    // ── Step-up authentication ──────────────────────────────────
    public static final String STEP_UP_TOKEN = "X-Step-Up-Token";

    // ── Timeout & Idempotency ───────────────────────────────────
    public static final String CLIENT_TIMEOUT_MS = "X-Client-Timeout-MS";
    public static final String IDEMPOTENCY_KEY   = "Idempotency-Key";

    // ── Patient-mediated share provenance (BFF → PCT, optional) ────
    public static final String PATIENT_SHARE_GRANT_ID = "X-Patient-Share-Grant-Id";
    public static final String VITO_CONTRIBUTION_ID = "X-Vito-Contribution-Id";
    public static final String TEMPORARY_PROVIDER_PUBLIC_ID = "X-Temporary-Provider-Public-Id";
    public static final String PATIENT_SHARE_CORRELATION_ID = "X-Patient-Share-Correlation-Id";
    public static final String EXTERNAL_PROVIDER_TRUST_LEVEL = "X-External-Provider-Trust-Level";

    // ── Policy Decision (gateway → service, injected after allow) ─
    public static final String POLICY_DECISION = "X-Policy-Decision";
    public static final String POLICY_VERSION  = "X-Policy-Version";
    public static final String DECISION_REASON = "X-Decision-Reason";

    /**
     * Experience BFF → clinical-knowledge-platform prescribing evaluate:
     * when present, overrides JSON {@code record_trace} after gateway policy merge.
     * Values: {@code true} / {@code false} (case-insensitive).
     */
    public static final String PRESCRIBING_RECORD_TRACE = "X-Impilo-Prescribing-Record-Trace";

    /**
     * When {@code true}, forces {@code record_trace: false} on prescribing evaluate (dry-run / sandbox).
     */
    public static final String SKIP_PRESCRIBING_TRACE = "X-Impilo-Skip-Prescribing-Trace";

    // ── Hard-required headers (missing any of these => 400) ─────
    // Manifest v1.2 spec: ALL FOUR must be present on every v1.2 request.
    public static final String[] HARD_REQUIRED = {
            TENANT_ID, POD_ID, REQUEST_ID, CORRELATION_ID
    };
}
