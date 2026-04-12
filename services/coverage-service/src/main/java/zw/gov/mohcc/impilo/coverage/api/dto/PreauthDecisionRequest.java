package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record PreauthDecisionRequest(
        @NotBlank String status,
        String decisionJson,
        String decisionEvidenceJson,
        BigDecimal quantityApproved) {}
