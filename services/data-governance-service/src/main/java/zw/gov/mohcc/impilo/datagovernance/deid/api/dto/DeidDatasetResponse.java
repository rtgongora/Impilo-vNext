package zw.gov.mohcc.impilo.datagovernance.deid.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record DeidDatasetResponse(
        @JsonProperty("dataset_id") UUID datasetId,
        @JsonProperty("dataset_code") String datasetCode,
        String name,
        String purpose,
        @JsonProperty("zone_target") String zoneTarget,
        @JsonProperty("policy_ref") String policyRef,
        @JsonProperty("secret_ref") String secretRef,
        String status,
        @JsonProperty("created_at") String createdAt
) {}
