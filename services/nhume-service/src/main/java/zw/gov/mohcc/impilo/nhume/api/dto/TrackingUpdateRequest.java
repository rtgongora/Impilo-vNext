package zw.gov.mohcc.impilo.nhume.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record TrackingUpdateRequest(
        @JsonProperty("captured_at") OffsetDateTime capturedAt,
        @JsonProperty("event_kind") String eventKind,
        @JsonProperty("lat") Double lat,
        @JsonProperty("lng") Double lng,
        @JsonProperty("accuracy_m") BigDecimal accuracyM,
        @JsonProperty("courier_id") UUID courierId,
        @JsonProperty("asset_id") UUID assetId,
        @JsonProperty("source") String source,
        @JsonProperty("payload") Map<String, Object> payload
) {}
