package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for cancelling an order.
 */
public record CancelRequest(
        @NotBlank String reason
) {}
