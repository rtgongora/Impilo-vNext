package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateCoveragePlanRequest(
        @NotBlank String planCode,
        @NotBlank String planName,
        @NotBlank String payerId,
        @NotBlank String planType,
        @NotNull LocalDate effectiveFrom
) {}
