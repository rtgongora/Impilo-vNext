package zw.gov.mohcc.impilo.pharmacy.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pharmacy.api.dto.BarcodeResultDto;
import zw.gov.mohcc.impilo.pharmacy.core.BarcodeLookupService;
import zw.gov.mohcc.impilo.pharmacy.core.FormularyService;
import zw.gov.mohcc.impilo.pharmacy.core.StockLedgerService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * REST controller for barcode lookup operations.
 *
 * <p>Supports scanning GTIN-14, GTIN-13, and internal barcodes to look up
 * item details including formulary status and current stock position.</p>
 */
@RestController
@RequestMapping("/v1/items")
public class BarcodeController {

    private static final Logger log = LoggerFactory.getLogger(BarcodeController.class);

    private final BarcodeLookupService barcodeLookupService;
    private final FormularyService formularyService;
    private final StockLedgerService stockLedgerService;

    public BarcodeController(BarcodeLookupService barcodeLookupService,
                             FormularyService formularyService,
                             StockLedgerService stockLedgerService) {
        this.barcodeLookupService = barcodeLookupService;
        this.formularyService = formularyService;
        this.stockLedgerService = stockLedgerService;
    }

    /**
     * Look up an item by its scanned barcode.
     *
     * <p>Decodes the barcode to extract item code, batch, and expiry,
     * then enriches the result with formulary status and current stock balance.</p>
     */
    @GetMapping("/lookup-by-barcode")
    public ResponseEntity<ApiResponse<BarcodeResultDto>> lookupByBarcode(
            @RequestParam String code) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        BarcodeLookupService.BarcodeResult result = barcodeLookupService.lookupByBarcode(code);

        boolean formularyAllowed = formularyService.isAllowed(
                ctx.tenantId(), ctx.facilityId(), result.itemCode());

        // Use a default store ID for balance lookup; in practice this would
        // come from the pharmacist's workspace context
        int currentStock = stockLedgerService.getStockBalance(
                ctx.facilityId(), "MAIN", result.itemCode());

        BarcodeResultDto dto = new BarcodeResultDto(
                result.itemCode(),
                result.itemDisplay(),
                result.batchNumber(),
                result.expiryDate(),
                formularyAllowed,
                currentStock);

        log.debug("Barcode lookup: code={}, item={}, formulary={}, stock={}",
                code, result.itemCode(), formularyAllowed, currentStock);

        return ResponseEntity.ok(ApiResponse.ok(dto, correlationId));
    }
}
