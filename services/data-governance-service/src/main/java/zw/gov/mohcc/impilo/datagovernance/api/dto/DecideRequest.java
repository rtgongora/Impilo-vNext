package zw.gov.mohcc.impilo.datagovernance.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record DecideRequest(
        @NotBlank @JsonProperty("principal_id") String principalId,
        @NotBlank String dataset,
        @NotBlank @JsonProperty("purpose_of_use") String purposeOfUse,
        @JsonProperty("query_fingerprint") String queryFingerprint,
        Map<String, String> context
) {}
