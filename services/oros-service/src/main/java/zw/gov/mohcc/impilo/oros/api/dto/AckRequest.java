package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.oros.domain.AckType;

/**
 * Request DTO for acknowledging an order.
 */
public record AckRequest(
        @NotNull AckType ackType,
        String notes
) {}
