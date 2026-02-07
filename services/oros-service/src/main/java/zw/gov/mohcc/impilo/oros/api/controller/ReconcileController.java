package zw.gov.mohcc.impilo.oros.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.MatchRequest;
import zw.gov.mohcc.impilo.oros.api.dto.ReconcileSummaryDto;
import zw.gov.mohcc.impilo.oros.api.dto.ResolveRequest;
import zw.gov.mohcc.impilo.oros.core.ReconciliationService;
import zw.gov.mohcc.impilo.oros.persistence.entity.ReconcileQueueEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for reconciliation operations.
 *
 * <p>Provides endpoints for viewing pending reconciliation entries
 * and for matching or resolving them.</p>
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
     * Get pending reconciliation entries for the current tenant.
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PagedResponse<ReconcileSummaryDto>>> getPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        Page<ReconcileQueueEntity> result = reconciliationService
                .getPendingReconciliations(PageRequest.of(page, size));

        List<ReconcileSummaryDto> items = result.getContent().stream()
                .map(ReconcileSummaryDto::from)
                .collect(Collectors.toList());

        PagedResponse<ReconcileSummaryDto> pagedResponse = PagedResponse.of(
                items, page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(pagedResponse, correlationId));
    }

    /**
     * Match a reconciliation entry to an existing order.
     */
    @PostMapping("/{recId}/match")
    public ResponseEntity<ApiResponse<ReconcileSummaryDto>> match(
            @PathVariable UUID recId,
            @Valid @RequestBody MatchRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ReconcileQueueEntity entry = reconciliationService
                .matchReconciliation(recId, request.orderId());

        return ResponseEntity.ok(ApiResponse.ok(ReconcileSummaryDto.from(entry), correlationId));
    }

    /**
     * Resolve a reconciliation entry with operational notes.
     */
    @PostMapping("/{recId}/resolve")
    public ResponseEntity<ApiResponse<ReconcileSummaryDto>> resolve(
            @PathVariable UUID recId,
            @Valid @RequestBody ResolveRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ReconcileQueueEntity entry = reconciliationService
                .resolveReconciliation(recId, request.notes());

        return ResponseEntity.ok(ApiResponse.ok(ReconcileSummaryDto.from(entry), correlationId));
    }
}
