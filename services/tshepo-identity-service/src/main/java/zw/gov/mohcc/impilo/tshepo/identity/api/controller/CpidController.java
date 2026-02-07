package zw.gov.mohcc.impilo.tshepo.identity.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.*;
import zw.gov.mohcc.impilo.tshepo.identity.core.CpidGenerator;
import zw.gov.mohcc.impilo.tshepo.identity.core.ReconciliationService;

import java.util.UUID;

/**
 * CPID generation endpoints.
 *
 * <p>Supports both deterministic canonical CPID generation and offline
 * provisional O-CPID issuance.</p>
 */
@RestController
@RequestMapping("/v1/identity/cpid")
public class CpidController {

    private final CpidGenerator cpidGenerator;
    private final ReconciliationService reconciliationService;

    public CpidController(CpidGenerator cpidGenerator,
                           ReconciliationService reconciliationService) {
        this.cpidGenerator = cpidGenerator;
        this.reconciliationService = reconciliationService;
    }

    /**
     * Generate a deterministic CPID (UUID v5) from tenantId + healthId.
     *
     * <p>This is a pure computation — it does NOT persist a mapping. Use
     * POST /v1/identity/mapping to persist. This endpoint is useful for
     * pre-flight checks or client-side CPID computation verification.</p>
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<CpidResponse>> generateCpid(
            @Valid @RequestBody GenerateCpidRequest request) {
        UUID cpid = cpidGenerator.generateCpid(request.tenantId(), request.healthId());
        return ResponseEntity.ok(ApiResponse.ok(new CpidResponse(cpid, false), null));
    }

    /**
     * Generate a provisional O-CPID for offline use.
     *
     * <p>The O-CPID is a random UUID tracked in the provisional_cpid table.
     * It must be reconciled to a canonical CPID once the facility is back online.</p>
     */
    @PostMapping("/provisional")
    public ResponseEntity<ApiResponse<ProvisionalCpidResponse>> generateProvisionalCpid(
            @Valid @RequestBody ProvisionalCpidRequest request) {
        ProvisionalCpidResponse result = reconciliationService.createProvisionalCpid(
                request.tenantId(), request.facilityId(), request.deviceFingerprint());
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }
}
