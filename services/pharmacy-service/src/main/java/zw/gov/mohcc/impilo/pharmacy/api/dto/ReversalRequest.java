package zw.gov.mohcc.impilo.pharmacy.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for reversing a dispense order entirely.
 *
 * @param reason the reason for the reversal
 */
public record ReversalRequest(
        @NotBlank String reason
) {}
