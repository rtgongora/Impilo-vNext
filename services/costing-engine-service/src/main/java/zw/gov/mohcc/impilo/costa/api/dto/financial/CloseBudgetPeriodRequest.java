package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CloseBudgetPeriodRequest(
        @NotNull UUID financialPeriodId,
        String status,
        BigDecimal carryForwardAmount,
        String notes
) {}
