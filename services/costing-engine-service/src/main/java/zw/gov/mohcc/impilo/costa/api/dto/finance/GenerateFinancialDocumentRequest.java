package zw.gov.mohcc.impilo.costa.api.dto.finance;

import jakarta.validation.constraints.NotBlank;

public record GenerateFinancialDocumentRequest(
        @NotBlank String docType,
        @NotBlank String subjectType,
        @NotBlank String subjectRef,
        String period,
        String recipientType,
        String recipientRef
) {}
