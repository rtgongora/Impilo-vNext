package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.ResolveReconcileRequest;
import zw.gov.mohcc.impilo.inventory.core.ReconciliationService;
import zw.gov.mohcc.impilo.inventory.domain.ReconcileStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ReconcileQueueEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for stock reconciliation operations.
 *
 * <p>Provides endpoints for viewing pending reconciliation entries
 * and resolving discrepancies between internal records and external
 * supply-chain systems (e.g. eLMIS).</p>
 */
@RestController
@RequestMapping("/v1/reconcile")
public class ReconcileController {

    private static final Logger log = LoggerFactory.getLogger(ReconcileController.class);

    private final ReconciliationService reconciliationService;

    public ReconcileController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * Get pending reconciliation entries for the current tenant (paginated).
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PagedResponse<ReconcileSummary>>> getPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        Page<ReconcileQueueEntity> result = reconciliationService.getPending(PageRequest.of(page, size));

        List<ReconcileSummary> items = result.getContent().stream()
                .map(ReconcileSummary::from)
                .collect(Collectors.toList());

        PagedResponse<ReconcileSummary> pagedResponse = PagedResponse.of(
                items, page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(pagedResponse, correlationId));
    }

    /**
     * Resolve a reconciliation entry with operator notes.
     */
    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<ReconcileSummary>> resolve(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveReconcileRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ReconcileQueueEntity entry = reconciliationService.resolve(id, request.notes());

        log.info("Reconciliation resolved: recId={}", entry.getRecId());

        return ResponseEntity.ok(ApiResponse.ok(ReconcileSummary.from(entry), correlationId));
    }

    /**
     * Inline summary record for reconciliation responses.
     */
    private record ReconcileSummary(
            UUID recId,
            UUID facilityId,
            UUID storeId,
            String itemCode,
            String batch,
            Integer internalQty,
            Integer externalQty,
            BigDecimal confidence,
            ReconcileStatus status,
            String opsNotes,
            String resolvedBy,
            OffsetDateTime resolvedAt,
            OffsetDateTime createdAt
    ) {
        static ReconcileSummary from(ReconcileQueueEntity entity) {
            return new ReconcileSummary(
                    entity.getRecId(),
                    entity.getFacilityId(),
                    entity.getStoreId(),
                    entity.getItemCode(),
                    entity.getBatch(),
                    entity.getInternalQty(),
                    entity.getExternalQty(),
                    entity.getConfidence(),
                    entity.getStatus(),
                    entity.getOpsNotes(),
                    entity.getResolvedBy(),
                    entity.getResolvedAt(),
                    entity.getCreatedAt());
        }
    }
}
