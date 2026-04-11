package zw.gov.mohcc.impilo.tshepo.core;

/**
 * Single source of truth for all trust-layer HTTP header names within TSHEPO.
 *
 * <p>Aligned with Health OS Manifest v1.2 (see docs/doctrine/health-os-doctrine.md §8, §11).
 * Must stay in sync with {@code tshepo-contracts/TrustHeaders.java} and
 * {@code CompanionHeaders.java}.</p>
 */
public final class TrustHeaders {

    private TrustHeaders() {}

    // ── Request headers (mandatory) ──────────────────────────────────
    public static final String TENANT_ID          = "x-tenant-id";
    public static final String ACTOR_ID           = "x-actor-id";
    public static final String ACTOR_TYPE         = "x-actor-type";
    public static final String PURPOSE_OF_USE     = "x-purpose-of-use";
    public static final String DEVICE_FINGERPRINT = "x-device-fingerprint";
    public static final String CORRELATION_ID     = "x-correlation-id";

    // ── Actor identity (Health OS §5–§6) ────────────────────────────
    public static final String PROVIDER_ID = "x-provider-id";

    // ── Operational context (Health OS §7) ───────────────────────────
    public static final String FACILITY_ID   = "x-facility-id";
    public static final String DEPARTMENT_ID = "x-department-id";
    public static final String WARD_ID       = "x-ward-id";
    public static final String WORKSPACE_ID  = "x-workspace-id";
    public static final String PROGRAMME_ID  = "x-programme-id";
    public static final String SHIFT_ID      = "x-shift-id";

    // ── Governance (Health OS §11) ───────────────────────────────────
    public static final String ASSURANCE_LEVEL = "x-assurance-level";
    public static final String SUBJECT_ID      = "x-subject-id";
    public static final String ACCESS_MODE     = "x-access-mode";
    public static final String WORKFLOW_STATE  = "x-workflow-state";

    // ── Response / obligation headers ────────────────────────────────
    public static final String DECISION      = "x-decision";
    public static final String OBLIGATIONS   = "x-obligations";
    public static final String MAX_SCOPE     = "x-max-scope";
    public static final String MASK_FIELDS   = "x-mask-fields";
    public static final String LOGGING_LEVEL = "x-logging-level";
}
