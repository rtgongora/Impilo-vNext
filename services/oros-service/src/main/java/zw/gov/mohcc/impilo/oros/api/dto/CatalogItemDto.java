package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.oros.domain.OrderType;

/**
 * DTO for a single catalog item in an import request.
 */
public record CatalogItemDto(
        @NotNull OrderType orderType,
        @NotBlank String code,
        @NotBlank String displayName,
        String category,
        String ziboCanonical
) {}
