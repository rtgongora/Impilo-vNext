package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateThresholdRequest(
        UUID budgetId,
        UUID budgetLineId,
        @NotBlank String metric,
        @NotNull BigDecimal warnAt,
        BigDecimal hardStopAt,
        Boolean allowEmergencyOverride
) {}
