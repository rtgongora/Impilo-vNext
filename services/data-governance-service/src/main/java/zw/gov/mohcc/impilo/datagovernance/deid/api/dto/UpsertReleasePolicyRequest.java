package zw.gov.mohcc.impilo.datagovernance.deid.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpsertReleasePolicyRequest(
        @NotBlank @JsonProperty("policy_code") String policyCode,
        @NotBlank String purpose,
        String requester,
        @JsonProperty("retention_days") Integer retentionDays,
        @JsonProperty("reid_prohibited") Boolean reidProhibited,
        @JsonProperty("k_threshold") Integer kThreshold,
        @JsonProperty("quasi_identifier_rules") JsonNode quasiIdentifierRules,
        @JsonProperty("allowlist_columns") List<String> allowlistColumns,
        String status
) {}
