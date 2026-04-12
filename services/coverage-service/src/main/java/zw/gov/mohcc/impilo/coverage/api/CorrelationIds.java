package zw.gov.mohcc.impilo.coverage.api;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Normalizes {@code X-Correlation-ID} to a UUID for persistence and outbox rows.
 * Valid UUID strings are preserved; any other non-blank value becomes a deterministic name-based UUID.
 */
public final class CorrelationIds {

    private CorrelationIds() {}

    public static UUID fromHeader(String header) {
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
