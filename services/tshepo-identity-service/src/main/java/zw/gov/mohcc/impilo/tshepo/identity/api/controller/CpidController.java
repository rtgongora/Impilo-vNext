package zw.gov.mohcc.impilo.tshepo.identity.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.*;
import zw.gov.mohcc.impilo.tshepo.identity.core.IdResolutionService;
import zw.gov.mohcc.impilo.tshepo.identity.core.ReconciliationService;

import java.util.UUID;

/**
 * CPID issuance endpoints.
 *
 * <p>Canonical CPIDs are independent random UUIDs held only in the id_mapping
 * table (Identity Contract §7) — there is no client-computable derivation, so
 * every issuance goes through the mapping.</p>
 */
@RestController
@RequestMapping("/v1/identity/cpid")
public class CpidController {

    private final IdResolutionService idResolutionService;
    private final ReconciliationService reconciliationService;

    public CpidController(IdResolutionService idResolutionService,
                           ReconciliationService reconciliationService) {
        this.idResolutionService = idResolutionService;
        this.reconciliationService = reconciliationService;
    }

    /**
     * Issue (find-or-create) the CPID for a tenantId + healthId pair.
     *
     * <p>Idempotent: repeated calls return the same mapped CPID. The former
     * "pure computation" contract is retired — CPIDs are random and only exist
     * once mapped, so nothing outside this service can compute one.</p>
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<CpidResponse>> generateCpid(
            @Valid @RequestBody GenerateCpidRequest request) {
        UUID cpid = idResolutionService.createMapping(new CreateMappingRequest(
                request.tenantId(), request.healthId(), null)).cpid();
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
