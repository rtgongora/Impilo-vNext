package zw.gov.mohcc.impilo.iotingestion.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record IngestTelemetryRequest(
        @NotBlank String deviceId,
        @NotBlank String metricType,
        @NotNull Double metricValue,
        String unit,
        @NotBlank String schemaVersion,
        @NotBlank String recordedAt,
        Map<String, Object> metadata) {
}
