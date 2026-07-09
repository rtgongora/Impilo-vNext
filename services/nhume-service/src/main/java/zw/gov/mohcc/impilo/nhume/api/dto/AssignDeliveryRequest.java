package zw.gov.mohcc.impilo.nhume.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AssignDeliveryRequest(
        @JsonProperty("courier_id") UUID courierId,
        @JsonProperty("asset_id") UUID assetId,
        @JsonProperty("integration_provider") String integrationProvider,
        @JsonProperty("integration_ref") String integrationRef,
        // Clients send "mode" (the transport mode chosen at assignment); accept
        // it as an alias so the mode is not silently dropped.
        @JsonProperty("mode_category") @JsonAlias({"mode"}) String modeCategory,
        @JsonProperty("assignment_kind") String assignmentKind,
        @JsonProperty("notes") String notes
) {}
