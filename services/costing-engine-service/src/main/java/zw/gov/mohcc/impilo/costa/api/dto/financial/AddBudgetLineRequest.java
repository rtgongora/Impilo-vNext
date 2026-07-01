package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AddBudgetLineRequest(
        UUID costCenterId,
        String departmentId,
        @NotBlank String budgetCategory,
        UUID fundingSourceId,
        String programmeCode,
        String grantId,
        String districtCode,
        String glAccountRef,
        @NotNull BigDecimal allocatedAmount,
        Integer lineOrder
) {}
