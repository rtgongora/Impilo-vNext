package zw.gov.mohcc.impilo.tshepo.identity.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.*;
import zw.gov.mohcc.impilo.tshepo.identity.core.ReconciliationService;

import java.util.List;
import java.util.UUID;

/**
 * Provisional CPID reconciliation endpoints.
 *
 * <p>When a facility operates offline, it issues O-CPIDs. Once connectivity
 * is restored, these must be reconciled to canonical CPIDs so that all
 * downstream data can be merged under the correct patient identity.</p>
 */
@RestController
@RequestMapping("/v1/identity")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * Reconcile a provisional O-CPID to a canonical CPID.
     *
     * <p>The healthId is resolved to a deterministic CPID, and the provisional
     * record is updated with the canonical CPID and status=RECONCILED.</p>
     */
    @PostMapping("/reconcile")
    public ResponseEntity<ApiResponse<ReconcileResponse>> reconcile(
            @Valid @RequestBody ReconcileRequest request) {
        ReconcileResponse result = reconciliationService.reconcile(request);
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }

    /**
     * List all unreconciled provisional O-CPIDs for a tenant.
     */
    @GetMapping("/provisional")
    public ResponseEntity<ApiResponse<List<ProvisionalCpidResponse>>> listUnreconciled(
            @RequestHeader("x-tenant-id") UUID tenantId) {
        List<ProvisionalCpidResponse> result = reconciliationService.listUnreconciled(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }
}
