package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateCoveragePlanRequest(
        @NotBlank String planCode,
        @NotBlank String planName,
        @NotBlank String payerId,
        @NotBlank String planType,
        @NotNull LocalDate effectiveFrom,
        // Ruvimbo (optional): link this flat live plan to the payer registry + a published plan version.
        UUID payerRef,
        UUID planVersionId
) {}
