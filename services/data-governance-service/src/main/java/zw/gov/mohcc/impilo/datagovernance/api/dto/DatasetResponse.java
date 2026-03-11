package zw.gov.mohcc.impilo.datagovernance.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record DatasetResponse(
        @JsonProperty("dataset_id") UUID datasetId,
        @JsonProperty("tenant_id") UUID tenantId,
        String name,
        String classification,
        String description,
        @JsonProperty("created_at") String createdAt
) {}
