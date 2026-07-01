package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBudgetRequest(
        UUID facilityId,
        String scopeLevel,
        @NotNull Integer periodYear,
        LocalDate periodStart,
        LocalDate periodEnd,
        @NotBlank String title,
        String grantId,
        String programmeCode,
        UUID fundingSourceId,
        String currency,
        String notes
) {}
