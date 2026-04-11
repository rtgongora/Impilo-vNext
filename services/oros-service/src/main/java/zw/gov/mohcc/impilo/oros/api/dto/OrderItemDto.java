package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for an individual order item within a placement request.
 *
 * @param codingSystem the terminology system URI (e.g. "http://loinc.org")
 * @param code         the item code within the coding system
 * @param displayName  human-readable name
 * @param quantity     number of units ordered
 * @param instructions free-text instructions
 * @param specimenType specimen type (for lab orders)
 * @param specimenTypeSystem system URI for specimen type coding
 * @param bodySite     target body site (for imaging/procedure orders)
 * @param bodySiteSystem system URI for body site coding
 */
public record OrderItemDto(
        String codingSystem,
        @NotBlank String code,
        String displayName,
        @Min(1) int quantity,
        String instructions,
        String specimenType,
        String specimenTypeSystem,
        String bodySite,
        String bodySiteSystem
) {}
