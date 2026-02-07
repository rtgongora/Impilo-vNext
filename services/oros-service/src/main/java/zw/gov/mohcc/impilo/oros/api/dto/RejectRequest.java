package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for rejecting an order.
 */
public record RejectRequest(
        @NotBlank String reason
) {}
