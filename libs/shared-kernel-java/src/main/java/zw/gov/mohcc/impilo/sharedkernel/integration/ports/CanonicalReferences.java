package zw.gov.mohcc.impilo.sharedkernel.integration.ports;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Canonical reference types used across ports. These are minimal,
 * de-identified envelopes — full PHI MUST be loaded by the canonical service
 * (not by adapters) under TSHEPO authorization.
 */
public final class CanonicalReferences {
    private CanonicalReferences() {}

    public record PatientReference(String impiloPatientId, String externalReference) {}
    public record ProviderReference(String impiloProviderId, String externalReference) {}
    public record FacilityReference(String impiloFacilityId, String externalReference) {}
    public record EncounterReference(String impiloEncounterId, String externalReference) {}
    public record OrderReference(String impiloOrderId, String externalReference) {}

    public record OperationContext(
            String tenantId,
            String correlationId,
            String purposeOfUse,
            String requestSource,
            Map<String, String> trustHeaders
    ) {}

    public record OperationResult<T>(
            boolean success,
            T value,
            String errorCode,
            String errorMessage,
            OffsetDateTime completedAt
    ) {
        public static <T> OperationResult<T> ok(T value) {
            return new OperationResult<>(true, value, null, null, OffsetDateTime.now());
        }
        public static <T> OperationResult<T> failure(String code, String message) {
            return new OperationResult<>(false, null, code, message, OffsetDateTime.now());
        }
    }
}
