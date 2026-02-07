package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.Min;

/**
 * Request DTO for updating a count line with the physically counted quantity.
 *
 * @param qtyCounted the physically counted quantity (must be non-negative)
 * @param barcode    optional barcode scanned during counting for verification
 */
public record UpdateCountLineRequest(
        @Min(0) int qtyCounted,
        String barcode
) {}
