package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for resolving a reconciliation queue entry.
 *
 * @param notes the operator's resolution notes (required)
 */
public record ResolveReconcileRequest(
        @NotBlank String notes
) {}
