package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vashandi.core.WorkforceContextService;
import zw.gov.mohcc.impilo.vashandi.core.WorkforceImportBridgeService;
import zw.gov.mohcc.impilo.vashandi.core.WorkforceProfileService;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/internal/vashandi/workforce-profiles")
public class VashandiWorkforceController {

    private final WorkforceProfileService profileService;
    private final WorkforceImportBridgeService importBridgeService;
    private final WorkforceContextService workforceContextService;

    public VashandiWorkforceController(WorkforceProfileService profileService,
                                       WorkforceImportBridgeService importBridgeService,
                                       WorkforceContextService workforceContextService) {
        this.profileService = profileService;
        this.importBridgeService = importBridgeService;
        this.workforceContextService = workforceContextService;
    }

    @GetMapping
    public List<WorkforceProfileEntity> list() {
        return profileService.list(tenantId());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkforceProfileEntity> get(@PathVariable UUID id) {
        return profileService.get(tenantId(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public WorkforceProfileEntity create(@RequestBody VashandiDtos.CreateWorkforceProfileRequest request) throws Exception {
        return profileService.create(tenantId(), request);
    }

    @PostMapping("/reconcile")
    public VashandiDtos.ReconcileProfileResponse reconcile(@RequestBody VashandiDtos.ReconcileProfileRequest request)
            throws Exception {
        return profileService.reconcile(tenantId(), request);
    }

    /**
     * Session-context read-model for the experience BFF session contract.
     * Resolves the caller's workforce profile by health id (person anchor),
     * falling back to provider-worker id; unknown actors get the same shape
     * with a no_workforce_profile resolution state (anti-enumeration).
     */
    @GetMapping("/session-context")
    public Map<String, Object> sessionContext(
            @RequestParam(value = "healthId", required = false) String healthId,
            @RequestParam(value = "providerWorkerId", required = false) String providerWorkerId) {
        return workforceContextService.sessionContext(tenantId(), healthId, providerWorkerId);
    }

    @PostMapping("/import-bridge")
    public VashandiDtos.ImportBridgeResponse importBridge(@RequestBody VashandiDtos.ImportBridgeRequest request)
            throws Exception {
        return importBridgeService.importRows(tenantId(), request);
    }

    private UUID tenantId() {
        TrustContext ctx = TrustContextHolder.require();
        return ctx.tenantId();
    }
}
