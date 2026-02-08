package zw.gov.mohcc.impilo.mushex.api.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ClaimAdjudicationRequest(
        String decision,
        @NotNull BigDecimal patientResidual,
        @NotNull BigDecimal insurerPayable
) {}
