package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.*;
import zw.gov.mohcc.impilo.inventory.core.ExternalSyncService;
import zw.gov.mohcc.impilo.inventory.domain.SyncStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ExternalSyncStateEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dura eLMIS/NatPharm external sync REST API ({@code /v1/dura/external-sync}).
 */
@RestController
@RequestMapping("/v1/dura/external-sync")
public class DuraExternalSyncController {

    private static final Logger log = LoggerFactory.getLogger(DuraExternalSyncController.class);

    private final ExternalSyncService syncService;

    public DuraExternalSyncController(ExternalSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExternalSyncStateDto>> enqueue(
            @Valid @RequestBody EnqueueSyncRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ExternalSyncStateEntity state = syncService.enqueue(
                request.system(), request.entityType(), request.entityRef(), request.payloadJson());
        log.info("Sync enqueued via API: system={}, entityRef={}", request.system(), request.entityRef());
        return ResponseEntity.ok(ApiResponse.ok(ExternalSyncStateDto.from(state), correlationId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExternalSyncStateDto>>> list(
            @RequestParam(value = "status", required = false) SyncStatus status) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<ExternalSyncStateDto> dtos = syncService.listByStatus(status).stream()
                .map(ExternalSyncStateDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @GetMapping("/{syncId}")
    public ResponseEntity<ApiResponse<ExternalSyncStateDto>> get(@PathVariable UUID syncId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                ExternalSyncStateDto.from(syncService.getState(syncId)), correlationId));
    }

    @PostMapping("/{syncId}/synced")
    public ResponseEntity<ApiResponse<ExternalSyncStateDto>> markSynced(
            @PathVariable UUID syncId,
            @RequestBody(required = false) MarkSyncedRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        String externalRef = request != null ? request.externalRef() : null;
        ExternalSyncStateEntity state = syncService.markSynced(syncId, externalRef);
        return ResponseEntity.ok(ApiResponse.ok(ExternalSyncStateDto.from(state), correlationId));
    }

    @PostMapping("/{syncId}/failed")
    public ResponseEntity<ApiResponse<ExternalSyncStateDto>> markFailed(
            @PathVariable UUID syncId,
            @Valid @RequestBody MarkSyncFailedRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ExternalSyncStateEntity state = syncService.markFailed(syncId, request.errorMessage());
        return ResponseEntity.ok(ApiResponse.ok(ExternalSyncStateDto.from(state), correlationId));
    }

    @PostMapping("/{syncId}/retry")
    public ResponseEntity<ApiResponse<ExternalSyncStateDto>> retry(@PathVariable UUID syncId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ExternalSyncStateEntity state = syncService.retry(syncId);
        return ResponseEntity.ok(ApiResponse.ok(ExternalSyncStateDto.from(state), correlationId));
    }

    @GetMapping("/{syncId}/errors")
    public ResponseEntity<ApiResponse<List<ExternalSyncErrorDto>>> errors(@PathVariable UUID syncId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<ExternalSyncErrorDto> dtos = syncService.errorHistory(syncId).stream()
                .map(ExternalSyncErrorDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }
}
