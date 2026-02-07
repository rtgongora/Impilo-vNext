package zw.gov.mohcc.impilo.tshepo.core;

/**
 * Single source of truth for all trust-layer HTTP header names.
 * These names form the contract between the UI, Envoy gateway, and all backend services.
 * Changing a name here is a breaking change across the entire platform.
 */
public final class TrustHeaders {

    private TrustHeaders() {}

    // --- Request headers (UI → Envoy → TSHEPO) ---
    public static final String TENANT_ID         = "x-tenant-id";
    public static final String ACTOR_ID          = "x-actor-id";
    public static final String ACTOR_TYPE        = "x-actor-type";
    public static final String PURPOSE_OF_USE    = "x-purpose-of-use";
    public static final String DEVICE_FINGERPRINT = "x-device-fingerprint";
    public static final String CORRELATION_ID    = "x-correlation-id";
    public static final String FACILITY_ID       = "x-facility-id";
    public static final String WORKSPACE_ID      = "x-workspace-id";
    public static final String SHIFT_ID          = "x-shift-id";

    // --- Response / obligation headers (TSHEPO → Envoy → downstream) ---
    public static final String DECISION          = "x-decision";
    public static final String OBLIGATIONS       = "x-obligations";
    public static final String MAX_SCOPE         = "x-max-scope";
    public static final String MASK_FIELDS       = "x-mask-fields";
    public static final String LOGGING_LEVEL     = "x-logging-level";
}
