package zw.gov.mohcc.impilo.datagovernance.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PublishPolicyRequest(
        @NotBlank String name,
        String description,
        @JsonProperty("rules_json") String rulesJson
) {}
