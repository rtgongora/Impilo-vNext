package zw.gov.mohcc.impilo.datagovernance.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDatasetRequest(
        @NotBlank String name,
        String classification,
        String description
) {}
