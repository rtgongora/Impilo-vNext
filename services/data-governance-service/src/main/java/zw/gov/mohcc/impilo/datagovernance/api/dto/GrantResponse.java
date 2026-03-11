package zw.gov.mohcc.impilo.datagovernance.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record GrantResponse(
        @JsonProperty("grant_id") UUID grantId,
        @JsonProperty("dataset_id") UUID datasetId,
        @JsonProperty("principal_id") String principalId,
        @JsonProperty("purpose_of_use") String purposeOfUse,
        @JsonProperty("expires_at") String expiresAt,
        @JsonProperty("created_at") String createdAt
) {}
