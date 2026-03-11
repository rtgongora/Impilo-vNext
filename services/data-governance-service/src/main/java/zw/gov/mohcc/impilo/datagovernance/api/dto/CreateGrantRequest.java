package zw.gov.mohcc.impilo.datagovernance.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateGrantRequest(
        @NotNull @JsonProperty("dataset_id") UUID datasetId,
        @NotBlank @JsonProperty("principal_id") String principalId,
        @NotBlank @JsonProperty("purpose_of_use") String purposeOfUse,
        @JsonProperty("expires_at") String expiresAt
) {}
