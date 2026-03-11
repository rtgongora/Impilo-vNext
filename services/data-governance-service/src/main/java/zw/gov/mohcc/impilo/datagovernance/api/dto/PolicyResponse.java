package zw.gov.mohcc.impilo.datagovernance.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record PolicyResponse(
        @JsonProperty("policy_id") UUID policyId,
        @JsonProperty("tenant_id") UUID tenantId,
        String name,
        int version,
        String description,
        String status,
        @JsonProperty("published_at") String publishedAt,
        @JsonProperty("created_at") String createdAt
) {}
