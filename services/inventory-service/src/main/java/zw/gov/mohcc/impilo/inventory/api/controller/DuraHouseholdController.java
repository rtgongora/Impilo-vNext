package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.*;
import zw.gov.mohcc.impilo.inventory.core.HouseholdStockService;
import zw.gov.mohcc.impilo.inventory.domain.HomeStockStatus;
import zw.gov.mohcc.impilo.inventory.domain.RefillStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ClientHomeStockEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RefillRequestEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dura client/caregiver household stock + refill REST API
 * ({@code /v1/dura/client-stock}, {@code /v1/dura/refills}).
 *
 * <p>Household stock is personal health data; the caller's authority over the
 * client is enforced by Tshepo/Vito at the gateway.</p>
 */
@RestController
@RequestMapping("/v1/dura")
public class DuraHouseholdController {

    private static final Logger log = LoggerFactory.getLogger(DuraHouseholdController.class);

    private final HouseholdStockService householdStockService;

    public DuraHouseholdController(HouseholdStockService householdStockService) {
        this.householdStockService = householdStockService;
    }

    // ── Household stock ─────────────────────────────────────────

    @PostMapping("/client-stock")
    public ResponseEntity<ApiResponse<ClientHomeStockDto>> addHomeStock(
            @Valid @RequestBody AddHomeStockRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ClientHomeStockEntity item = householdStockService.addHomeStock(
                request.clientId(), request.itemCode(), request.itemName(), request.qty(),
                request.uom(), request.batchNumber(), request.expiryDate(),
                request.source(), request.managedByCaregiver(), request.notes());
        log.info("Home stock added via API: client={}, item={}", request.clientId(), request.itemName());
        return ResponseEntity.ok(ApiResponse.ok(ClientHomeStockDto.from(item), correlationId));
    }

    @GetMapping("/client-stock")
    public ResponseEntity<ApiResponse<List<ClientHomeStockDto>>> listHomeStock(
            @RequestParam("clientId") String clientId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<ClientHomeStockDto> dtos = householdStockService.listHomeStock(clientId).stream()
                .map(ClientHomeStockDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @PatchMapping("/client-stock/{homeStockId}/quantity")
    public ResponseEntity<ApiResponse<ClientHomeStockDto>> updateQuantity(
            @PathVariable UUID homeStockId,
            @Valid @RequestBody UpdateHomeStockQuantityRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ClientHomeStockEntity item = householdStockService.updateQuantity(homeStockId, request.qty());
        return ResponseEntity.ok(ApiResponse.ok(ClientHomeStockDto.from(item), correlationId));
    }

    @PostMapping("/client-stock/{homeStockId}/status")
    public ResponseEntity<ApiResponse<ClientHomeStockDto>> setHomeStockStatus(
            @PathVariable UUID homeStockId,
            @RequestParam("status") HomeStockStatus status) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ClientHomeStockEntity item = householdStockService.setHomeStockStatus(homeStockId, status);
        return ResponseEntity.ok(ApiResponse.ok(ClientHomeStockDto.from(item), correlationId));
    }

    // ── Refills ─────────────────────────────────────────────────

    @PostMapping("/refills")
    public ResponseEntity<ApiResponse<RefillRequestDto>> requestRefill(
            @Valid @RequestBody CreateRefillRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        RefillRequestEntity refill = householdStockService.requestRefill(
                request.clientId(), request.itemCode(), request.itemName(),
                request.qtyRequested(), request.preferredFacilityId(), request.notes());
        log.info("Refill requested via API: client={}, item={}", request.clientId(), request.itemName());
        return ResponseEntity.ok(ApiResponse.ok(RefillRequestDto.from(refill), correlationId));
    }

    @GetMapping("/refills")
    public ResponseEntity<ApiResponse<List<RefillRequestDto>>> listRefills(
            @RequestParam("clientId") String clientId,
            @RequestParam(value = "status", required = false) RefillStatus status) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<RefillRequestDto> dtos = householdStockService.listRefills(clientId, status).stream()
                .map(RefillRequestDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @PostMapping("/refills/{refillId}/status")
    public ResponseEntity<ApiResponse<RefillRequestDto>> updateRefillStatus(
            @PathVariable UUID refillId,
            @Valid @RequestBody UpdateRefillStatusRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        RefillRequestEntity refill = householdStockService.updateRefillStatus(
                refillId, request.status(), request.notes());
        return ResponseEntity.ok(ApiResponse.ok(RefillRequestDto.from(refill), correlationId));
    }
}
