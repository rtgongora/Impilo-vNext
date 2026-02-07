package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.core.BarcodeLookupService;

import java.time.LocalDate;

/**
 * DTO for barcode lookup results containing resolved item and batch details.
 *
 * @param itemCode    the resolved item code
 * @param itemDisplay the human-readable item name
 * @param batchNumber the batch number extracted from the barcode
 * @param expiryDate  the expiry date extracted from the barcode
 * @param valid       whether the barcode resolved to a valid item
 */
public record BarcodeResultDto(
        String itemCode,
        String itemDisplay,
        String batchNumber,
        LocalDate expiryDate,
        boolean valid
) {

    /**
     * Map a BarcodeResult from the core service to a DTO.
     *
     * @param result the core service barcode result
     * @return the DTO representation
     */
    public static BarcodeResultDto from(BarcodeLookupService.BarcodeResult result) {
        return new BarcodeResultDto(
                result.itemCode(),
                result.itemDisplay(),
                result.batchNumber(),
                result.expiryDate(),
                result.valid()
        );
    }
}
