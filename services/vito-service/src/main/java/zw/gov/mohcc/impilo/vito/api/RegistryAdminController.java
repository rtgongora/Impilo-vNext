package zw.gov.mohcc.impilo.vito.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.vito.core.OpenCrAdapter;
import zw.gov.mohcc.impilo.vito.core.StandaloneRegistryService;
import zw.gov.mohcc.impilo.vito.persistence.entity.DedupCaseEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ProvisionalIdEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Registry Admin API — Mode A (OpenCR) and Mode B (Standalone).
 * All operations are INTERNAL only.
 */
@RestController
@RequestMapping("/v1/registry")
public class RegistryAdminController {

    private final StandaloneRegistryService standaloneService;
    private final OpenCrAdapter openCrAdapter;

    public RegistryAdminController(StandaloneRegistryService standaloneService,
                                    OpenCrAdapter openCrAdapter) {
        this.standaloneService = standaloneService;
        this.openCrAdapter = openCrAdapter;
    }

    // --- Mode configuration ---

    @GetMapping("/mode")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMode() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("mode", openCrAdapter.isEnabled() ? "OPENCR" : "STANDALONE",
                        "opencrEnabled", openCrAdapter.isEnabled()),
                ctx.correlationId().toString()));
    }

    // --- Provisional IDs (Mode B: Standalone) ---

    @PostMapping("/provisional/issue")
    public ResponseEntity<ApiResponse<ProvisionalIdEntity>> issueProvisionalId(
            @RequestBody ProvisionalIdRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);

        ProvisionalIdEntity provisional = standaloneService.issueProvisionalId(
                ctx.tenantId(), request.facilityId(), ctx.actorId(), request.expiryHours());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(provisional, ctx.correlationId().toString()));
    }

    @PostMapping("/provisional/{provisionalRef}/reconcile")
    public ResponseEntity<ApiResponse<ProvisionalIdEntity>> reconcile(
            @PathVariable String provisionalRef,
            @RequestBody ReconcileRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);

        ProvisionalIdEntity reconciled = standaloneService.reconcile(
                ctx.tenantId(), provisionalRef, request.healthId());

        return ResponseEntity.ok(ApiResponse.ok(reconciled, ctx.correlationId().toString()));
    }

    @GetMapping("/provisional/pending")
    public ResponseEntity<ApiResponse<PagedResponse<ProvisionalIdEntity>>> getPendingProvisionalIds(
            @RequestParam UUID facilityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();
        Page<ProvisionalIdEntity> results = standaloneService.getPendingProvisionalIds(
                ctx.tenantId(), facilityId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(results.getContent(), page, size, results.getTotalElements()),
                ctx.correlationId().toString()));
    }

    // --- Dedup cases ---

    @GetMapping("/dedup/pending")
    public ResponseEntity<ApiResponse<PagedResponse<DedupCaseEntity>>> getPendingDedupCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();
        Page<DedupCaseEntity> results = standaloneService.getPendingDedupCases(
                ctx.tenantId(), PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(results.getContent(), page, size, results.getTotalElements()),
                ctx.correlationId().toString()));
    }

    @PostMapping("/dedup/{caseId}/resolve")
    public ResponseEntity<ApiResponse<DedupCaseEntity>> resolveDedupCase(
            @PathVariable Long caseId,
            @RequestBody DedupResolveRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);

        DedupCaseEntity resolved = standaloneService.resolveDedupCase(
                caseId, request.decision(), ctx.actorId());
        return ResponseEntity.ok(ApiResponse.ok(resolved, ctx.correlationId().toString()));
    }

    // --- OpenCR interop (Mode A) ---

    @PostMapping("/opencr/match")
    public ResponseEntity<ApiResponse<String>> openCrMatch(@RequestBody OpenCrMatchRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        String result = openCrAdapter.fhirMatch(
                request.givenName(), request.familyName(),
                request.dateOfBirth(), request.sex());

        if (result == null) {
            return ResponseEntity.ok(ApiResponse.ok(
                    "OpenCR not enabled or unreachable", ctx.correlationId().toString()));
        }

        return ResponseEntity.ok(ApiResponse.ok(result, ctx.correlationId().toString()));
    }

    private void requireInternal(TrustContext ctx) {
        if (ctx.mode() == AccessMode.EXTERNAL) {
            throw new SecurityException("Registry admin operations are internal only");
        }
    }

    record ProvisionalIdRequest(UUID facilityId, int expiryHours) {}
    record ReconcileRequest(UUID healthId) {}
    record DedupResolveRequest(String decision) {}
    record OpenCrMatchRequest(String givenName, String familyName, String dateOfBirth, String sex) {}
}
