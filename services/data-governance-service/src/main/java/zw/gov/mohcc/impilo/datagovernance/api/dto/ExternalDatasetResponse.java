package zw.gov.mohcc.impilo.datagovernance.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record ExternalDatasetResponse(
        @JsonProperty("dataset_id") UUID datasetId,
        String name,
        String classification
) {}
