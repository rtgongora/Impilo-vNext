package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DecideAppealRequest(
        @NotBlank String decision,
        String decisionReason,
        @NotBlank String decidedBy
) {}
