package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClosePeriodRequest(
        @NotBlank String periodType,
        @NotNull @Min(2000) @Max(2100) Integer year,
        @NotNull @Min(1) @Max(12) Integer month
) {}
