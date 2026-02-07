package zw.gov.mohcc.impilo.inventory.core;

import java.time.LocalDate;

/**
 * Service for looking up items by barcode and validating batch data.
 *
 * <p>Supports GTIN-14, GTIN-13, and internal barcode formats.
 * Parses the barcode to extract the item code, then queries the
 * item registry to provide lookup results.</p>
 *
 * <h3>Barcode Format Detection</h3>
 * <ul>
 *   <li>14 digits: GTIN-14 — strip packaging indicator (pos 0) and check digit (pos 13) to get 12-char code</li>
 *   <li>13 digits: GTIN-13 — strip check digit (pos 12) to get 12-char code</li>
 *   <li>Otherwise: treated as an internal code and used as-is</li>
 * </ul>
 */
public interface BarcodeLookupService {

    /**
     * Look up an item by its scanned barcode.
     *
     * @param barcode the scanned barcode string
     * @return the lookup result with item details and validity
     */
    BarcodeResult lookupByBarcode(String barcode);

    /**
     * Validate a batch number and expiry date combination for an item.
     *
     * @param itemCode   the item code
     * @param batchNumber the batch number to validate
     * @param expiryDate  the expiry date to check
     * @return the validation result
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
