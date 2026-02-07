package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for matching a reconciliation entry to an order.
 */
public record MatchRequest(
        @NotBlank String orderId
) {}
