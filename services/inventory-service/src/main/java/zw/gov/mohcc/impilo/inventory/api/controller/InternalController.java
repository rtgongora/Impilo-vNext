package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.ConsumptionRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.LedgerEventDto;
import zw.gov.mohcc.impilo.inventory.core.CapabilityRoutingService;
import zw.gov.mohcc.impilo.inventory.core.ConsumptionPostingService;
import zw.gov.mohcc.impilo.inventory.core.StoreService;
import zw.gov.mohcc.impilo.inventory.domain.BinType;
import zw.gov.mohcc.impilo.inventory.domain.CapabilityMode;
import zw.gov.mohcc.impilo.inventory.domain.StoreType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BinEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.LedgerEventEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StoreEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;
import java.util.UUID;

/**
 * Internal REST controller for inter-service communication and administrative operations.
 *
 * <p>Provides endpoints for capability mode management, consumption posting
 * from downstream clinical systems, and store/bin creation. These endpoints
 * are intended for platform-internal calls only.</p>
 */
@RestController
@RequestMapping("/v1/internal")
public class InternalController {

    private static final Logger log = LoggerFactory.getLogger(InternalController.class);

    private final CapabilityRoutingService capabilityRoutingService;
    private final ConsumptionPostingService consumptionPostingService;
    private final StoreService storeService;

    public InternalController(CapabilityRoutingService capabilityRoutingService,
                              ConsumptionPostingService consumptionPostingService,
                              StoreService storeService) {
        this.capabilityRoutingService = capabilityRoutingService;
        this.consumptionPostingService = consumptionPostingService;
        this.storeService = storeService;
    }

    /**
     * Refresh the capability mode for a tenant/facility.
     */
    @PostMapping("/capability/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refreshCapability(
            @RequestBody Map<String, String> payload) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        UUID tenantId = UUID.fromString(payload.get("tenantId"));
        UUID facilityId = UUID.fromString(payload.get("facilityId"));
        CapabilityMode mode = CapabilityMode.valueOf(payload.get("mode"));

        capabilityRoutingService.setMode(tenantId, facilityId, mode);

        log.info("Capability mode set: tenantId={}, facilityId={}, mode={}", tenantId, facilityId, mode);

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("tenantId", tenantId.toString(),
                       "facilityId", facilityId.toString(),
                       "mode", mode.name()),
                correlationId));
    }

    /**
     * Post a pharmacy consumption event (from pharmacy-service integration).
     */
    @PostMapping("/consumption/pharmacy")
    public ResponseEntity<ApiResponse<LedgerEventDto>> postPharmacyConsumption(
            @Valid @RequestBody ConsumptionRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        LedgerEventEntity event = consumptionPostingService.postPharmacyConsumption(
                request.facilityId(), request.storeId(), request.itemCode(),
                request.batch(), request.expiry(), request.qty(), request.orderId());

        log.info("Pharmacy consumption posted: itemCode={}, qty={}, orderId={}",
                request.itemCode(), request.qty(), request.orderId());

        return ResponseEntity.ok(ApiResponse.ok(LedgerEventDto.from(event), correlationId));
    }

    /**
     * Post a lab consumption event (from OROS lab integration).
     */
    @PostMapping("/consumption/lab")
    public ResponseEntity<ApiResponse<LedgerEventDto>> postLabConsumption(
            @Valid @RequestBody ConsumptionRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        LedgerEventEntity event = consumptionPostingService.postLabConsumption(
                request.facilityId(), request.storeId(), request.itemCode(),
                request.qty(), request.orderId());

        log.info("Lab consumption posted: itemCode={}, qty={}, orderId={}",
                request.itemCode(), request.qty(), request.orderId());

        return ResponseEntity.ok(ApiResponse.ok(LedgerEventDto.from(event), correlationId));
    }

    /**
     * Post a clinical consumption event (ward, theatre, procedure).
     */
    @PostMapping("/consumption/clinical")
    public ResponseEntity<ApiResponse<LedgerEventDto>> postClinicalConsumption(
            @Valid @RequestBody ConsumptionRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        LedgerEventEntity event = consumptionPostingService.postClinicalConsumption(
                request.facilityId(), request.storeId(), request.itemCode(),
                request.qty(), request.refType(), request.orderId());

        log.info("Clinical consumption posted: itemCode={}, qty={}, refType={}",
                request.itemCode(), request.qty(), request.refType());

        return ResponseEntity.ok(ApiResponse.ok(LedgerEventDto.from(event), correlationId));
    }

    /**
     * Create a new store within a facility.
     */
    @PostMapping("/stores")
    public ResponseEntity<ApiResponse<StoreSummary>> createStore(
            @RequestBody Map<String, String> payload) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        UUID facilityId = UUID.fromString(payload.get("facilityId"));
        String name = payload.get("name");
        StoreType type = StoreType.valueOf(payload.getOrDefault("type", "GENERAL"));
        UUID parentStoreId = payload.containsKey("parentStoreId")
                ? UUID.fromString(payload.get("parentStoreId"))
                : null;

        StoreEntity store = storeService.createStore(facilityId, name, type, parentStoreId);

        log.info("Store created: storeId={}, name={}, facilityId={}", store.getStoreId(), name, facilityId);

        return ResponseEntity.ok(ApiResponse.ok(
                new StoreSummary(store.getStoreId(), store.getFacilityId(), store.getName(),
                        store.getStoreType().name(), store.getParentStoreId()),
                correlationId));
    }

    /**
     * Create a new bin within a store.
     */
    @PostMapping("/stores/{storeId}/bins")
    public ResponseEntity<ApiResponse<BinSummary>> createBin(
            @PathVariable UUID storeId,
            @RequestBody Map<String, String> payload) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        String name = payload.get("name");
        BinType type = BinType.valueOf(payload.getOrDefault("type", "GENERAL"));

        BinEntity bin = storeService.createBin(storeId, name, type);

        log.info("Bin created: binId={}, name={}, storeId={}", bin.getBinId(), name, storeId);

        return ResponseEntity.ok(ApiResponse.ok(
                new BinSummary(bin.getBinId(), bin.getStoreId(), bin.getName(), bin.getBinType().name()),
                correlationId));
    }

    /**
     * Inline summary record for store responses.
     */
    private record StoreSummary(
            UUID storeId,
            UUID facilityId,
            String name,
            String type,
            UUID parentStoreId
    ) {}

    /**
     * Inline summary record for bin responses.
     */
    private record BinSummary(
            UUID binId,
            UUID storeId,
            String name,
            String type
    ) {}
}
