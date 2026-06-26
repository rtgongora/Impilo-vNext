package zw.gov.mohcc.impilo.patientsafety.api;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Resolves the mandatory trust headers carried on every internal request into a small context.
 * Tenant is required; correlation is normalised to a UUID; actor/pod default sensibly so a missing
 * optional header never NPEs a handler.
 */
public record RequestContext(
        UUID tenantId,
        String podId,
        UUID correlationId,
        String actorId,
        String actorType,
        String actorRole) {

    public static RequestContext of(String tenantId, String podId, String correlationId,
                                    String actorId, String actorType, String purposeOfUse) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("X-Tenant-ID header is required");
        }
        return new RequestContext(
                UUID.fromString(tenantId.trim()),
                (podId == null || podId.isBlank()) ? "national-spine" : podId.trim(),
                normalizeCorrelation(correlationId),
                actorId,
                actorType,
                purposeOfUse);
    }

    private static UUID normalizeCorrelation(String header) {
        if (header == null || header.isBlank()) {
            return UUID.randomUUID();
        }
        String trimmed = header.trim();
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(trimmed.getBytes(StandardCharsets.UTF_8));
        }
    }
}
