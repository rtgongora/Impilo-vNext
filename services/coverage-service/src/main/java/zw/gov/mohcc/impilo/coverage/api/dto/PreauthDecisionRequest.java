package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;

public record PreauthDecisionRequest(
        @NotBlank String status,
        String decisionJson,
        String decisionEvidenceJson
) {}
