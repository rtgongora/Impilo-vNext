package zw.gov.mohcc.impilo.pharmacy.core;

import java.time.LocalDate;

/**
 * Service for looking up items by barcode and validating batch data.
 *
 * <p>Supports GTIN-14, GTIN-13, and internal barcode formats.
 * Returns decoded item code, batch number, and expiry information.</p>
 */
public interface BarcodeLookupService {

    /**
     * Look up an item by its scanned barcode.
     */
    BarcodeResult lookupByBarcode(String barcode);

    /**
     * Validate a batch number and expiry date combination.
     */
    BatchValidation validateBatch(String itemCode, String batchNumber, LocalDate expiryDate);

    /**
     * Result of a barcode lookup.
     */
    record BarcodeResult(
            String itemCode,
            String itemDisplay,
            String batchNumber,
            LocalDate expiryDate,
            boolean valid
    ) {}

    /**
     * Result of a batch validation.
     */
    record BatchValidation(
            boolean valid,
            boolean expired,
            String message
    ) {}
}
