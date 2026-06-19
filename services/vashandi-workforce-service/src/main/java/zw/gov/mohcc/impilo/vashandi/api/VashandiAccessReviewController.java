package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vashandi.core.WorkforceAccessReviewService;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AccessRiskEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/internal/vashandi/access-risks")
public class VashandiAccessReviewController {

    private final WorkforceAccessReviewService accessReviewService;

    public VashandiAccessReviewController(WorkforceAccessReviewService accessReviewService) {
        this.accessReviewService = accessReviewService;
    }

    @GetMapping
    public List<AccessRiskEntity> list(@RequestParam(value = "status", required = false) String status) {
        return accessReviewService.list(tenantId(), status);
    }

    @PostMapping("/scan")
    public VashandiDtos.AccessRiskScanResponse scan() throws Exception {
        return accessReviewService.scan(tenantId());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<AccessRiskEntity> resolve(@PathVariable UUID id) throws Exception {
        return ResponseEntity.ok(accessReviewService.resolve(tenantId(), id));
    }

    @PostMapping("/{id}/revoke-access")
    public VashandiDtos.AccessRiskActionResponse revokeAccess(@PathVariable UUID id) throws Exception {
        return accessReviewService.revokeAccess(tenantId(), id);
    }

    private UUID tenantId() {
        TrustContext ctx = TrustContextHolder.require();
        return ctx.tenantId();
    }
}
