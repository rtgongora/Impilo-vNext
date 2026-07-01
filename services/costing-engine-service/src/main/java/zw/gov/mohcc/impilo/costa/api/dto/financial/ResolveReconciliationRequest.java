package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.NotBlank;

public record ResolveReconciliationRequest(@NotBlank String status, String note) {}
