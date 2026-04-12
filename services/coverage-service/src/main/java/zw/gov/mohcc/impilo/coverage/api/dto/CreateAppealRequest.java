package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAppealRequest(
        @NotBlank String claimId,
        String coverageId,
        @NotBlank String reason,
        String evidenceJson,
        String appellantType,
        String appellantId) {}
