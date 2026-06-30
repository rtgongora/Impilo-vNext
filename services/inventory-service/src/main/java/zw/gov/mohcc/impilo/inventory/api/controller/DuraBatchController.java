package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.BatchLotDto;
import zw.gov.mohcc.impilo.inventory.api.dto.RegisterBatchRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.SetBatchStatusRequest;
import zw.gov.mohcc.impilo.inventory.core.BatchLotService;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BatchLotEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dura batch/lot REST API ({@code /v1/dura/batches}).
 */
@RestController
@RequestMapping("/v1/dura/batches")
public class DuraBatchController {

    private static final Logger log = LoggerFactory.getLogger(DuraBatchController.class);

    private final BatchLotService batchLotService;

    public DuraBatchController(BatchLotService batchLotService) {
        this.batchLotService = batchLotService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BatchLotDto>> registerBatch(
            @Valid @RequestBody RegisterBatchRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        BatchLotEntity batch = batchLotService.registerBatch(
                request.itemCode(), request.batchNumber(), request.lotNumber(),
                request.expiryDate(), request.manufactureDate(), request.manufacturer(),
                request.supplier(), request.serialNumber(), request.notes());
        log.info("Batch registered via API: itemCode={}, batch={}", request.itemCode(), request.batchNumber());
        return ResponseEntity.ok(ApiResponse.ok(BatchLotDto.from(batch), correlationId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BatchLotDto>>> listForItem(@RequestParam("itemCode") String itemCode) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<BatchLotDto> dtos = batchLotService.listBatchesForItem(itemCode).stream()
                .map(BatchLotDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @GetMapping("/near-expiry")
    public ResponseEntity<ApiResponse<List<BatchLotDto>>> nearExpiry(
            @RequestParam(value = "days", defaultValue = "90") int days) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<BatchLotDto> dtos = batchLotService.nearExpiry(days).stream()
                .map(BatchLotDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @GetMapping("/{batchLotId}")
    public ResponseEntity<ApiResponse<BatchLotDto>> getBatch(@PathVariable UUID batchLotId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                BatchLotDto.from(batchLotService.getBatch(batchLotId)), correlationId));
    }

    @PostMapping("/{batchLotId}/status")
    public ResponseEntity<ApiResponse<BatchLotDto>> setStatus(
            @PathVariable UUID batchLotId,
            @Valid @RequestBody SetBatchStatusRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        BatchLotEntity batch = batchLotService.setStatus(batchLotId, request.status(), request.reason());
        log.info("Batch status set via API: batchLotId={}, status={}", batchLotId, request.status());
        return ResponseEntity.ok(ApiResponse.ok(BatchLotDto.from(batch), correlationId));
    }
}
