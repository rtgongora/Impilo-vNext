package zw.gov.mohcc.impilo.pharmacy.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pharmacy.api.dto.ReconcileSummaryDto;
import zw.gov.mohcc.impilo.pharmacy.api.dto.ResolveRequest;
import zw.gov.mohcc.impilo.pharmacy.core.ReconciliationService;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.ReconcileQueueEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for stock reconciliation operations.
 *
 * <p>Provides endpoints for viewing pending stock reconciliation entries
 * and resolving discrepancies between internal records and external systems.</p>
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
     * Get pending stock reconciliation entries for the current tenant.
     */
    @GetMapping("/stock/pending")
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
     * Resolve a stock reconciliation entry with operational notes.
     */
    @PostMapping("/stock/{id}/resolve")
    public ResponseEntity<ApiResponse<ReconcileSummaryDto>> resolve(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ReconcileQueueEntity entry = reconciliationService
                .resolveReconciliation(id, request.notes());

        log.info("Reconciliation resolved: recId={}", entry.getRecId());

        return ResponseEntity.ok(ApiResponse.ok(ReconcileSummaryDto.from(entry), correlationId));
    }
}
