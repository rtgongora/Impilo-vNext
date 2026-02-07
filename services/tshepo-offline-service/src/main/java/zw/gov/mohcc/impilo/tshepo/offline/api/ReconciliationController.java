package zw.gov.mohcc.impilo.tshepo.offline.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.ReconciliationRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.ReconciliationResponse;
import zw.gov.mohcc.impilo.tshepo.offline.core.ReconciliationService;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for offline action reconciliation.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *     <li>POST /v1/offline/reconcile — submit a batch of offline actions for reconciliation</li>
 *     <li>GET /v1/offline/reconcile/{batchId} — get batch reconciliation status</li>
 *     <li>GET /v1/offline/reconcile/pending — list pending reconciliation batches</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/offline/reconcile")
public class ReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * Submit a batch of offline actions for reconciliation.
     * Actions are validated, O-CPIDs reconciled, and records created.
     */
    @PostMapping
    public ResponseEntity<ReconciliationResponse> submitBatch(
            @Valid @RequestBody ReconciliationRequest request,
            @RequestHeader(TrustHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(TrustHeaders.ACTOR_ID) String actorId,
            @RequestHeader(TrustHeaders.CORRELATION_ID) UUID correlationId) {

        log.info("POST /v1/offline/reconcile — tenant={}, facility={}, actor={}, actionCount={}",
                tenantId, request.facilityId(), actorId, request.actions().size());

        ReconciliationResponse response = reconciliationService.submitBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get the status of a reconciliation batch.
     */
    @GetMapping("/{batchId}")
    public ResponseEntity<ReconciliationResponse> getBatchStatus(
            @PathVariable UUID batchId,
            @RequestHeader(TrustHeaders.TENANT_ID) UUID tenantId) {

        log.debug("GET /v1/offline/reconcile/{} — tenant={}", batchId, tenantId);

        ReconciliationResponse response = reconciliationService.getBatchStatus(batchId, tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * List pending (unprocessed) reconciliation batches for the tenant.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<ReconciliationResponse>> listPendingBatches(
            @RequestHeader(TrustHeaders.TENANT_ID) UUID tenantId) {

        log.debug("GET /v1/offline/reconcile/pending — tenant={}", tenantId);

        List<ReconciliationResponse> responses = reconciliationService.listPendingBatches(tenantId);
        return ResponseEntity.ok(responses);
    }
}
