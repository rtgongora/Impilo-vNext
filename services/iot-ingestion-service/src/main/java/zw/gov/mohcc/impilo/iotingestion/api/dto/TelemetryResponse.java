package zw.gov.mohcc.impilo.iotingestion.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TelemetryResponse(
        UUID readingId,
        String deviceId,
        UUID tenantId,
        String metricType,
        double metricValue,
        String unit,
        String schemaVersion,
        OffsetDateTime recordedAt,
        OffsetDateTime ingestedAt,
        String source) {
}
