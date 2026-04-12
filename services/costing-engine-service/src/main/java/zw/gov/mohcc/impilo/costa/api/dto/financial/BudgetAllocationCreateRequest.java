package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetAllocationCreateRequest(
        @NotNull UUID facilityId,
        String departmentId,
        @NotBlank String budgetCategory,
        @NotNull Integer periodYear,
        @NotNull BigDecimal allocatedAmount
) {}
