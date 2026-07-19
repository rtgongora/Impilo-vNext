package zw.gov.mohcc.impilo.datagovernance.deid.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CreateDeidDatasetRequest(
        @NotBlank @JsonProperty("dataset_code") String datasetCode,
        @NotBlank String name,
        @NotBlank String purpose,
        @JsonProperty("zone_target") String zoneTarget,
        @JsonProperty("policy_ref") String policyRef,
        @NotBlank @JsonProperty("secret_ref") String secretRef
) {}
