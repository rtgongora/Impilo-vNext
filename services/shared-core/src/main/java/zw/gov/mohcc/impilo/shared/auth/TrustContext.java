package zw.gov.mohcc.impilo.shared.auth;

import java.util.UUID;

/**
 * Immutable trust context extracted from HTTP request headers.
 *
 * Populated by {@link TrustContextFilter} and available to all controllers
 * via {@link TrustContextHolder}. Carries the full identity and authorization
 * context for the current request.
 */
public record TrustContext(
        UUID tenantId,
        String actorId,
        String actorType,
        String purposeOfUse,
        String deviceFingerprint,
        UUID correlationId,
        UUID facilityId,
        UUID workspaceId,
        String shiftId,
        AccessMode mode
) {
    /** Header names — must match TrustHeaders.java in tshepo-service and contracts.ts in UI */
    public static final String H_TENANT_ID          = "x-tenant-id";
    public static final String H_ACTOR_ID           = "x-actor-id";
    public static final String H_ACTOR_TYPE         = "x-actor-type";
    public static final String H_PURPOSE_OF_USE     = "x-purpose-of-use";
    public static final String H_DEVICE_FINGERPRINT = "x-device-fingerprint";
    public static final String H_CORRELATION_ID     = "x-correlation-id";
    public static final String H_FACILITY_ID        = "x-facility-id";
    public static final String H_WORKSPACE_ID       = "x-workspace-id";
    public static final String H_SHIFT_ID           = "x-shift-id";
    public static final String H_ACCESS_MODE        = "x-access-mode";
}
