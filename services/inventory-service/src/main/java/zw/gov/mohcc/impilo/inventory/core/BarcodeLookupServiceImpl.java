package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ItemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Barcode parsing and item lookup service implementation.
 *
 * <p>Supports GTIN-14, GTIN-13, and internal barcode formats. Parses the
 * barcode to extract a normalized item code, then queries the item repository
 * by barcode or item code to find the matching item.</p>
 *
 * <p>Batch validation checks expiry dates against the current date to
 * flag expired stock during receiving or counting operations.</p>
 */
@Service
public class BarcodeLookupServiceImpl implements BarcodeLookupService {

    private static final Logger log = LoggerFactory.getLogger(BarcodeLookupServiceImpl.class);

    private final ItemRepository itemRepository;

    public BarcodeLookupServiceImpl(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Lookup strategy:
     * <ol>
     *   <li>Parse the barcode to extract the item code using GTIN rules</li>
     *   <li>Search by barcode field first (exact match on raw barcode)</li>
     *   <li>If not found, search by the parsed item code</li>
     *   <li>Return validity based on whether a matching item was found</li>
     * </ol>
     */
    @Override
    public BarcodeResult lookupByBarcode(String barcode) {
        TrustContextHolder.require(); // ensure trust context exists

        if (barcode == null || barcode.isBlank()) {
            log.warn("Barcode lookup called with null or blank barcode");
            return new BarcodeResult(null, null, null, null, false);
        }

        String cleaned = barcode.trim();
        String parsedCode = parseGtin(cleaned);

        // Strategy 1: Search by raw barcode field
        Optional<ItemEntity> item = itemRepository.findFirstByBarcode(cleaned);

        // Strategy 2: Fall back to parsed item code
        if (item.isEmpty()) {
            item = itemRepository.findByItemCode(parsedCode);
        }

        // Strategy 3: Try with the parsed GTIN code as barcode
        if (item.isEmpty() && !parsedCode.equals(cleaned)) {
            item = itemRepository.findFirstByBarcode(parsedCode);
        }

        if (item.isPresent()) {
            ItemEntity found = item.get();
            log.debug("Barcode lookup hit: barcode={}, itemCode={}, name={}",
                    cleaned, found.getItemCode(), found.getName());
            return new BarcodeResult(
                    found.getItemCode(),
                    found.getName(),
                    null, // batch number not encoded in standard barcodes
                    null, // expiry not encoded in standard barcodes
                    true
            );
        }

        log.debug("Barcode lookup miss: barcode={}, parsedCode={}", cleaned, parsedCode);
        return new BarcodeResult(parsedCode, null, null, null, false);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates that:
     * <ul>
     *   <li>The item code and batch number are provided</li>
     *   <li>The batch has not expired (expiry date is not before today)</li>
     * </ul>
     */
    @Override
    public BatchValidation validateBatch(String itemCode, String batchNumber, LocalDate expiryDate) {
        TrustContextHolder.require(); // ensure trust context exists

        if (itemCode == null || itemCode.isBlank()) {
            return new BatchValidation(false, false, "Item code is required");
        }
        if (batchNumber == null || batchNumber.isBlank()) {
            return new BatchValidation(false, false, "Batch number is required");
        }

        // Verify item exists
        Optional<ItemEntity> item = itemRepository.findByItemCode(itemCode);
        if (item.isEmpty()) {
            return new BatchValidation(false, false,
                    "Item not found: " + itemCode);
        }

        // Check expiry
        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            return new BatchValidation(false, true,
                    "Batch " + batchNumber + " expired on " + expiryDate);
        }

        // Expiry date not provided — valid but with a warning
        if (expiryDate == null) {
            return new BatchValidation(true, false,
                    "Batch " + batchNumber + " valid (no expiry date provided)");
        }

        return new BatchValidation(true, false,
                "Batch " + batchNumber + " valid, expires " + expiryDate);
    }

    // ------------------------------------------------------------------
    // GTIN parsing
    // ------------------------------------------------------------------

    /**
     * Parse a barcode to extract the item code.
     *
     * <p>GTIN-14 (14 digits): packaging indicator (pos 0) + 12-digit item code + check digit (pos 13).
     * Extracts positions 1..12.</p>
     *
     * <p>GTIN-13 (13 digits): 12-digit item code + check digit (pos 12).
     * Extracts positions 0..11.</p>
     *
     * <p>All other formats: returned as-is (treated as internal code).</p>
     *
     * @param barcode the scanned barcode string (already trimmed)
     * @return the extracted item code
     */
    static String parseGtin(String barcode) {
        if (barcode == null) {
            return null;
        }

        if (barcode.length() == 14 && barcode.chars().allMatch(Character::isDigit)) {
            // GTIN-14: skip packaging indicator (pos 0) and check digit (pos 13)
            return barcode.substring(1, 13);
        }

        if (barcode.length() == 13 && barcode.chars().allMatch(Character::isDigit)) {
            // GTIN-13: skip check digit (pos 12)
            return barcode.substring(0, 12);
        }

        // Internal code: return as-is
        return barcode;
    }
}
