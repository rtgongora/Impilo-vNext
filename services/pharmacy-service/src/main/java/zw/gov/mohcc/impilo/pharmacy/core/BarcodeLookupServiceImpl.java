package zw.gov.mohcc.impilo.pharmacy.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.FormularyEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.FormularyRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.StockMovementRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Barcode parsing and item lookup service.
 *
 * <p>Supports GTIN-14, GTIN-13, and internal barcode formats. Parses the
 * barcode to extract the item code, then queries the formulary and stock
 * ledger to provide comprehensive lookup results including current stock
 * balance and formulary allowance status.</p>
 *
 * <h3>Barcode Format Detection</h3>
 * <ul>
 *   <li>14 digits: GTIN-14 (extract digits 2-13 as item code)</li>
 *   <li>13 digits: GTIN-13 (extract digits 1-12 as item code)</li>
 *   <li>Otherwise: treated as an internal code</li>
 * </ul>
 */
@Service
public class BarcodeLookupServiceImpl implements BarcodeLookupService {

    private static final Logger log = LoggerFactory.getLogger(BarcodeLookupServiceImpl.class);

    private final FormularyRepository formularyRepository;
    private final StockMovementRepository stockMovementRepository;

    public BarcodeLookupServiceImpl(FormularyRepository formularyRepository,
                                    StockMovementRepository stockMovementRepository) {
        this.formularyRepository = formularyRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Override
    public BarcodeResult lookupByBarcode(String barcode) {
        TrustContext ctx = TrustContextHolder.require();

        if (barcode == null || barcode.isBlank()) {
            return new BarcodeResult(null, null, null, null, false);
        }

        String itemCode = parseGtin(barcode);

        // Look up in formulary: facility-level first, then tenant-level
        Optional<FormularyEntity> formularyEntry =
                formularyRepository.findByTenantIdAndFacilityIdAndDrugCode(
                        ctx.tenantId(), ctx.facilityId(), itemCode);
        if (formularyEntry.isEmpty()) {
            formularyEntry = formularyRepository.findByTenantIdAndFacilityIdIsNullAndDrugCode(
                    ctx.tenantId(), itemCode);
        }

        String itemDisplay = formularyEntry.map(FormularyEntity::getDrugDisplay).orElse(itemCode);
        boolean valid = formularyEntry.map(FormularyEntity::isAllowed).orElse(false);

        log.debug("Barcode lookup: barcode={}, itemCode={}, valid={}", barcode, itemCode, valid);
        return new BarcodeResult(itemCode, itemDisplay, null, null, valid);
    }

    @Override
    public BatchValidation validateBatch(String itemCode, String batchNumber, LocalDate expiryDate) {
        TrustContext ctx = TrustContextHolder.require();

        if (itemCode == null || batchNumber == null) {
            return new BatchValidation(false, false, "Item code and batch number are required");
        }

        // Check if batch has expired
        boolean expired = expiryDate != null && expiryDate.isBefore(LocalDate.now());
        if (expired) {
            return new BatchValidation(false, true,
                    "Batch " + batchNumber + " expired on " + expiryDate);
        }

        // Check stock availability for this batch
        int batchBalance = stockMovementRepository.getStockBalanceByBatch(
                ctx.facilityId(), "DEFAULT", itemCode, batchNumber);
        if (batchBalance <= 0) {
            return new BatchValidation(false, false,
                    "Batch " + batchNumber + " has no stock available (balance: " + batchBalance + ")");
        }

        return new BatchValidation(true, false,
                "Batch " + batchNumber + " valid with " + batchBalance + " units available");
    }

    /**
     * Parse a barcode to extract the item code.
     *
     * <p>GTIN-14 (14 digits): indicator digit + 12 item digits + check digit.
     * Extracts the 12-digit item code (positions 1-12).</p>
     *
     * <p>GTIN-13 (13 digits): 12 item digits + check digit.
     * Extracts the 12-digit item code (positions 0-11).</p>
     *
     * <p>All other formats are treated as internal codes and returned as-is.</p>
     *
     * @param barcode the scanned barcode string
     * @return the extracted item code
     */
    static String parseGtin(String barcode) {
        if (barcode == null) {
            return null;
        }

        String cleaned = barcode.trim();

        if (cleaned.length() == 14 && cleaned.chars().allMatch(Character::isDigit)) {
            // GTIN-14: skip indicator (pos 0) and check digit (pos 13)
            return cleaned.substring(1, 13);
        }

        if (cleaned.length() == 13 && cleaned.chars().allMatch(Character::isDigit)) {
            // GTIN-13: skip check digit (pos 12)
            return cleaned.substring(0, 12);
        }

        // Internal code: return as-is
        return cleaned;
    }
}
