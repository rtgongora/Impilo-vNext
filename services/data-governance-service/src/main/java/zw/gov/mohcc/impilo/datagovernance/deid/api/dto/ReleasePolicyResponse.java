package zw.gov.mohcc.impilo.datagovernance.deid.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record ReleasePolicyResponse(
        @JsonProperty("policy_id") UUID policyId,
        @JsonProperty("policy_code") String policyCode,
        String purpose,
        String requester,
        @JsonProperty("retention_days") Integer retentionDays,
        @JsonProperty("reid_prohibited") boolean reidProhibited,
        @JsonProperty("k_threshold") int kThreshold,
        @JsonProperty("quasi_identifier_rules") JsonNode quasiIdentifierRules,
        @JsonProperty("allowlist_columns") JsonNode allowlistColumns,
        String status,
        int version,
        @JsonProperty("created_at") String createdAt
) {}
