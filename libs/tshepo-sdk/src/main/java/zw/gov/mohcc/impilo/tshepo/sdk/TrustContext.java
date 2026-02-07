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
        AccessMode mode
) {
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
