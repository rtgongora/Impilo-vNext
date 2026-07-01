package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

public record OpenReconciliationRequest(
        UUID budgetId,
        UUID budgetLineId,
        @NotBlank String exceptionType,
        String source,
        String sourceReference,
        BigDecimal expected,
        BigDecimal observed
) {}
