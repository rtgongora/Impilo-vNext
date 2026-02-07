package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.LedgerEventDto;
import zw.gov.mohcc.impilo.inventory.api.dto.LedgerPostRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.TransferRequest;
import zw.gov.mohcc.impilo.inventory.core.LedgerService;
import zw.gov.mohcc.impilo.inventory.domain.LedgerEventType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.LedgerEventEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for the stock ledger.
 *
 * <p>Provides endpoints for posting ledger events (receipts, issues, transfers,
 * adjustments, wastage, returns) and querying the event history. Each event
 * is immutable and affects the on-hand projection atomically.</p>
 */
@RestController
@RequestMapping("/v1/ledger")
public class LedgerController {

    private static final Logger log = LoggerFactory.getLogger(LedgerController.class);

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /**
     * Post a receipt event (goods received into stock, positive qty).
     */
    @PostMapping("/receipt")
    public ResponseEntity<ApiResponse<LedgerEventDto>> postReceipt(
            @Valid @RequestBody LedgerPostRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        LedgerEventEntity event = ledgerService.postEvent(new LedgerService.LedgerEventData(
                request.facilityId(), request.storeId(), request.binId(),
                request.itemCode(), request.batch(), request.expiry(),
                request.qty(),
                request.uom(), LedgerEventType.RECEIPT,
                request.reason(), request.refType(), request.refId(),
                request.idempotencyKey()));

        log.info("Receipt posted: itemCode={}, qty={}, facilityId={}",
                request.itemCode(), request.qty(), request.facilityId());

        return ResponseEntity.ok(ApiResponse.ok(LedgerEventDto.from(event), correlationId));
    }

    /**
     * Post an issue event (goods issued out of stock, negative qty).
     */
    @PostMapping("/issue")
    public ResponseEntity<ApiResponse<LedgerEventDto>> postIssue(
            @Valid @RequestBody LedgerPostRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        LedgerEventEntity event = ledgerService.postEvent(new LedgerService.LedgerEventData(
                request.facilityId(), request.storeId(), request.binId(),
                request.itemCode(), request.batch(), request.expiry(),
                -request.qty(),
                request.uom(), LedgerEventType.ISSUE,
                request.reason(), request.refType(), request.refId(),
                request.idempotencyKey()));

        log.info("Issue posted: itemCode={}, qty={}, facilityId={}",
                request.itemCode(), request.qty(), request.facilityId());

        return ResponseEntity.ok(ApiResponse.ok(LedgerEventDto.from(event), correlationId));
    }

    /**
     * Post a transfer: TRANSFER_OUT from source + TRANSFER_IN to destination.
     */
    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<Map<String, LedgerEventDto>>> postTransfer(
            @Valid @RequestBody TransferRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        LedgerEventEntity outEvent = ledgerService.postEvent(new LedgerService.LedgerEventData(
                request.sourceFacilityId(), request.sourceStoreId(), null,
                request.itemCode(), request.batch(), request.expiry(),
                -request.qty(),
                request.uom(), LedgerEventType.TRANSFER_OUT,
                request.reason(), "TRANSFER", request.idempotencyKey(),
                request.idempotencyKey() + "-OUT"));

        LedgerEventEntity inEvent = ledgerService.postEvent(new LedgerService.LedgerEventData(
                request.destFacilityId(), request.destStoreId(), null,
                request.itemCode(), request.batch(), request.expiry(),
                request.qty(),
                request.uom(), LedgerEventType.TRANSFER_IN,
                request.reason(), "TRANSFER", request.idempotencyKey(),
                request.idempotencyKey() + "-IN"));

        log.info("Transfer posted: itemCode={}, qty={}, from={}:{}, to={}:{}",
                request.itemCode(), request.qty(),
                request.sourceFacilityId(), request.sourceStoreId(),
                request.destFacilityId(), request.destStoreId());

        Map<String, LedgerEventDto> result = Map.of(
                "transferOut", LedgerEventDto.from(outEvent),
                "transferIn", LedgerEventDto.from(inEvent));

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    /**
     * Post an adjustment event (manual correction).
     */
    @PostMapping("/adjust")
    public ResponseEntity<ApiResponse<LedgerEventDto>> postAdjustment(
            @Valid @RequestBody LedgerPostRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        LedgerEventEntity event = ledgerService.postEvent(new LedgerService.LedgerEventData(
                request.facilityId(), request.storeId(), request.binId(),
                request.itemCode(), request.batch(), request.expiry(),
                request.qty(),
                request.uom(), LedgerEventType.ADJUSTMENT,
                request.reason(), request.refType(), request.refId(),
                request.idempotencyKey()));

        log.info("Adjustment posted: itemCode={}, qty={}, facilityId={}",
                request.itemCode(), request.qty(), request.facilityId());

        return ResponseEntity.ok(ApiResponse.ok(LedgerEventDto.from(event), correlationId));
    }

    /**
     * Post a wastage event (damaged/expired stock written off, negative qty).
     */
    @PostMapping("/wastage")
    public ResponseEntity<ApiResponse<LedgerEventDto>> postWastage(
            @Valid @RequestBody LedgerPostRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        LedgerEventEntity event = ledgerService.postEvent(new LedgerService.LedgerEventData(
                request.facilityId(), request.storeId(), request.binId(),
                request.itemCode(), request.batch(), request.expiry(),
                -request.qty(),
                request.uom(), LedgerEventType.WASTAGE,
                request.reason(), request.refType(), request.refId(),
                request.idempotencyKey()));

        log.info("Wastage posted: itemCode={}, qty={}, facilityId={}",
                request.itemCode(), request.qty(), request.facilityId());

        return ResponseEntity.ok(ApiResponse.ok(LedgerEventDto.from(event), correlationId));
    }

    /**
     * Post a return event (stock returned to supplier, positive qty back to stock).
     */
    @PostMapping("/return")
    public ResponseEntity<ApiResponse<LedgerEventDto>> postReturn(
            @Valid @RequestBody LedgerPostRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        LedgerEventEntity event = ledgerService.postEvent(new LedgerService.LedgerEventData(
                request.facilityId(), request.storeId(), request.binId(),
                request.itemCode(), request.batch(), request.expiry(),
                request.qty(),
                request.uom(), LedgerEventType.RETURN,
                request.reason(), request.refType(), request.refId(),
                request.idempotencyKey()));

        log.info("Return posted: itemCode={}, qty={}, facilityId={}",
                request.itemCode(), request.qty(), request.facilityId());

        return ResponseEntity.ok(ApiResponse.ok(LedgerEventDto.from(event), correlationId));
    }

    /**
     * Query ledger events with optional filters (paginated).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<LedgerEventDto>>> getLedgerEvents(
            @RequestParam UUID facilityId,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String itemCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        Page<LedgerEventEntity> result = ledgerService.getLedgerEvents(
                facilityId, storeId, itemCode, PageRequest.of(page, size));

        List<LedgerEventDto> items = result.getContent().stream()
                .map(LedgerEventDto::from)
                .collect(Collectors.toList());

        PagedResponse<LedgerEventDto> pagedResponse = PagedResponse.of(
                items, page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(pagedResponse, correlationId));
    }
}
