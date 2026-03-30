package zw.gov.mohcc.impilo.tshepo.sdk;

import zw.gov.mohcc.impilo.tshepo.contracts.enums.AccessMode;

import java.util.UUID;

/**
 * Immutable trust context extracted from HTTP request headers.
 *
 * <p>Populated by {@link filter.TrustContextFilter} at the start of each request
 * and available to all layers via {@link TrustContextHolder}.</p>
 *
 * <p>Carries the full identity and authorization context for the current request,
 * including tenant isolation, actor identity, purpose-of-use, and facility/workspace scope.</p>
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
        String podId,
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
    public static final String H_POD_ID             = "x-pod-id";
    public static final String H_ACCESS_MODE        = "x-access-mode";

    /**
     * Check if this context has facility-level scope.
     */
    public boolean hasFacilityScope() {
        return facilityId != null;
    }

    /**
     * Check if this context has workspace-level scope.
     */
    public boolean hasWorkspaceScope() {
        return workspaceId != null;
    }

    /**
     * Check if this is a service-to-service (internal) request.
     */
    public boolean isInternal() {
        return mode == AccessMode.INTERNAL;
    }
}
