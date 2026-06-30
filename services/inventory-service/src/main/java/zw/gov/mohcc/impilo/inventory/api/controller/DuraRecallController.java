package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.BatchLotDto;
import zw.gov.mohcc.impilo.inventory.api.dto.CloseRecallRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.CreateRecallRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.RecallEventDto;
import zw.gov.mohcc.impilo.inventory.core.RecallService;
import zw.gov.mohcc.impilo.inventory.domain.RecallStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RecallEventEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dura recall REST API ({@code /v1/dura/recalls}).
 */
@RestController
@RequestMapping("/v1/dura/recalls")
public class DuraRecallController {

    private static final Logger log = LoggerFactory.getLogger(DuraRecallController.class);

    private final RecallService recallService;

    public DuraRecallController(RecallService recallService) {
        this.recallService = recallService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RecallEventDto>> createRecall(
            @Valid @RequestBody CreateRecallRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        RecallEventEntity recall = recallService.createRecall(
                request.recallReference(), request.itemCode(), request.batchNumber(),
                request.source(), request.severity(), request.reason());
        log.info("Recall created via API: itemCode={}, frozen={}", request.itemCode(), recall.getAffectedBatches());
        return ResponseEntity.ok(ApiResponse.ok(RecallEventDto.from(recall), correlationId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RecallEventDto>>> list(
            @RequestParam(value = "status", required = false) RecallStatus status) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<RecallEventDto> dtos = recallService.listRecalls(status).stream()
                .map(RecallEventDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @GetMapping("/{recallId}")
    public ResponseEntity<ApiResponse<RecallEventDto>> get(@PathVariable UUID recallId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                RecallEventDto.from(recallService.getRecall(recallId)), correlationId));
    }

    @GetMapping("/{recallId}/affected-batches")
    public ResponseEntity<ApiResponse<List<BatchLotDto>>> affectedBatches(@PathVariable UUID recallId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<BatchLotDto> dtos = recallService.affectedBatches(recallId).stream()
                .map(BatchLotDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @PostMapping("/{recallId}/close")
    public ResponseEntity<ApiResponse<RecallEventDto>> close(
            @PathVariable UUID recallId,
            @RequestBody(required = false) CloseRecallRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        String note = request != null ? request.closureNote() : null;
        RecallEventEntity recall = recallService.closeRecall(recallId, note);
        return ResponseEntity.ok(ApiResponse.ok(RecallEventDto.from(recall), correlationId));
    }
}
