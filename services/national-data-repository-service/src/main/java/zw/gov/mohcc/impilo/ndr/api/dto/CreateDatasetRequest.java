package zw.gov.mohcc.impilo.ndr.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDatasetRequest(
        @NotBlank @Size(max = 128) String datasetKey,
        @NotBlank @Size(max = 256) String name,
        String description,
        @Size(max = 128) String owner
) {}
