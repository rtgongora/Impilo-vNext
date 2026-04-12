package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateProviderContractRequest(
        @NotBlank String providerId,
        @NotBlank String payerId,
        String planCode,
        String contractType,
        String reimbursementMode,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String rateScheduleJson,
        String termsJson,
        String createdBy
) {
}
