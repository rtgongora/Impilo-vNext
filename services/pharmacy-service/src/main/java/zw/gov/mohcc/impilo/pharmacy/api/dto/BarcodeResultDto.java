package zw.gov.mohcc.impilo.pharmacy.api.dto;

import java.time.LocalDate;

/**
 * DTO for barcode lookup results containing item details and stock status.
 *
 * @param itemCode         the drug/item code
 * @param itemDisplay      the human-readable drug name
 * @param batchNumber      the batch number decoded from the barcode
 * @param expiryDate       the expiry date decoded from the barcode
 * @param formularyAllowed whether the item is on the formulary at this facility
 * @param currentStock     the current stock balance at the facility store
 */
public record BarcodeResultDto(
        String itemCode,
        String itemDisplay,
        String batchNumber,
        LocalDate expiryDate,
        boolean formularyAllowed,
        int currentStock
) {}
