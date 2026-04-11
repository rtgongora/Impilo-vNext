package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.oros.domain.OrderType;

/**
 * DTO for a single catalog item in an import request.
 *
 * @param orderType     the type of order this item supports
 * @param codingSystem  terminology system URI (e.g. "http://loinc.org")
 * @param code          the item code within the coding system
 * @param displayName   human-readable name
 * @param category      optional category grouping
 * @param ziboCanonical ZIBO canonical reference URL
 */
public record CatalogItemDto(
        @NotNull OrderType orderType,
        String codingSystem,
        @NotBlank String code,
        @NotBlank String displayName,
        String category,
        String ziboCanonical
) {}
