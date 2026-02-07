package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for an individual order item within a placement request.
 */
public record OrderItemDto(
        @NotBlank String code,
        String displayName,
        @Min(1) int quantity,
        String instructions,
        String specimenType,
        String bodySite
) {}
