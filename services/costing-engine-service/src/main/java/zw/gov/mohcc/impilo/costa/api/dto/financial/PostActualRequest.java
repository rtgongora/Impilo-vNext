package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PostActualRequest(
        @NotNull UUID budgetLineId,
        @NotBlank String source,
        @NotBlank String sourceReference,
        UUID commitmentId,
        @NotNull BigDecimal actualAmount,
        String ingestSource
) {}
