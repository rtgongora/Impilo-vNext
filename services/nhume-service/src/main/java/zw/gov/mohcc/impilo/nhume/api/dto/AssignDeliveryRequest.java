package zw.gov.mohcc.impilo.nhume.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AssignDeliveryRequest(
        @JsonProperty("courier_id") UUID courierId,
        @JsonProperty("asset_id") UUID assetId,
        @JsonProperty("integration_provider") String integrationProvider,
        @JsonProperty("integration_ref") String integrationRef,
        @JsonProperty("mode_category") String modeCategory,
        @JsonProperty("assignment_kind") String assignmentKind,
        @JsonProperty("notes") String notes
) {}
