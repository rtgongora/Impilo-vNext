package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.Min;

public record RollbackConfigRequest(
        @Min(value = 1, message = "Target version must be at least 1")
        int targetVersion
) {}
