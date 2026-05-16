package zw.gov.mohcc.impilo.ndila.core.policy;

import java.util.UUID;

/**
 * Per-request operation context used by NdilaProviderPolicyService and
 * NdilaTshepoGuard. Captures the tenant, actor, purpose-of-use, use case,
 * sensitivity, offline-mode flag, emergency-mode flag and correlation ID.
 *
 * <p>All these fields are read from trust headers (when present) and from the
 * use-case-specific service invoking Ndila.
 */
public record NdilaOperationContext(
        UUID tenantId,
        String actorId,
        String actorType,
        String purposeOfUse,
        String useCase,
        String requestingService,
        NdilaSensitivity sensitivity,
        boolean offlineMode,
        boolean emergencyMode,
        UUID correlationId,
        String environment,
        String country
) {
    public static NdilaOperationContext minimal(UUID tenantId, String useCase) {
        return new NdilaOperationContext(
                tenantId, null, null, "OPERATIONS_MANAGEMENT", useCase,
                null, NdilaSensitivity.PUBLIC, false, false,
                UUID.randomUUID(), "development", "ZW");
    }
}
