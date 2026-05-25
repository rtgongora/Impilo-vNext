package zw.gov.mohcc.impilo.shared.auth;

/**
 * Canonical HTTP header names for trust/context propagation.
 *
 * Keep this list as the single source for extended trust and integration headers
 * used across service-to-service calls.
 */
public final class TrustHeaders {
    private TrustHeaders() {}

    public static final String X_TENANT_ID = "X-Tenant-ID";
    public static final String X_CORRELATION_ID = "X-Correlation-ID";
    public static final String X_DEVICE_FINGERPRINT = "X-Device-Fingerprint";
    public static final String X_PURPOSE_OF_USE = "X-Purpose-Of-Use";
    public static final String X_ACTOR_ID = "X-Actor-Id";
    public static final String X_ACTOR_TYPE = "X-Actor-Type";
    public static final String X_FACILITY_ID = "X-Facility-Id";
    public static final String X_WORKSPACE_ID = "X-Workspace-Id";
    public static final String X_SHIFT_ID = "X-Shift-Id";

    public static final String X_SERVICE_ID = "X-Service-Id";
    public static final String X_SERVICE_NAME = "X-Service-Name";
    public static final String X_SERVICE_VERSION = "X-Service-Version";
    public static final String X_REQUEST_SOURCE = "X-Request-Source";
    public static final String X_EXTERNAL_APP_ID = "X-External-App-Id";
    public static final String X_INTEGRATION_TYPE = "X-Integration-Type";
    public static final String X_INTEGRATION_VERSION = "X-Integration-Version";
    public static final String X_REQUEST_SIGNATURE = "X-Request-Signature";
    public static final String X_AI_SKILL_ID = "X-AI-Skill-Id";
    public static final String X_AI_MODEL_REF = "X-AI-Model-Ref";
}
