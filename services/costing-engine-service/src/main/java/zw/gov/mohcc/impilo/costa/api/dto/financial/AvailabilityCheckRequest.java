package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AvailabilityCheckRequest(
        @NotNull UUID budgetLineId,
        @NotNull BigDecimal requestedAmount,
        Boolean emergency,
        Boolean clinicalEmergency
) {}
