package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.web.bind.annotation.RequestHeader;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaOperationContext;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaSensitivity;

import java.util.UUID;

/**
 * Helper that extracts the Ndila operation context from canonical Impilo
 * trust headers. Mirrors the contract enforced across all Impilo services.
 */
public final class NdilaTrustHeaders {

    private NdilaTrustHeaders() {}

    public static NdilaOperationContext fromHeaders(
            String tenantId,
            String actorId,
            String actorType,
            String purposeOfUse,
            String useCase,
            String requestingService,
            String sensitivity,
            String correlationId,
            String environment,
            String country) {
        UUID tenant = parseUuid(tenantId);
        UUID corr = parseUuid(correlationId);
        return new NdilaOperationContext(
                tenant == null ? UUID.fromString("00000000-0000-0000-0000-000000000000") : tenant,
                actorId,
                actorType,
                purposeOfUse == null ? "OPERATIONS_MANAGEMENT" : purposeOfUse,
                useCase == null ? "GENERAL" : useCase,
                requestingService == null ? "UNKNOWN" : requestingService,
                NdilaSensitivity.parse(sensitivity),
                false,
                "BREAK_GLASS".equalsIgnoreCase(purposeOfUse) || "EMERGENCY_RESPONSE".equalsIgnoreCase(purposeOfUse),
                corr == null ? UUID.randomUUID() : corr,
                environment == null ? "development" : environment,
                country == null ? "ZW" : country);
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
