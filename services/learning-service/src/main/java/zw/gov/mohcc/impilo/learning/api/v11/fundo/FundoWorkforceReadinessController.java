package zw.gov.mohcc.impilo.learning.api.v11.fundo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoTrainingGateService;
import zw.gov.mohcc.impilo.learning.fundo.FundoWorkforceReadinessService;

/**
 * Trusted server-to-server training-evidence surface consumed by
 * vashandi-workforce-service. Lives under {@code /v1/internal/fundo/**} (a distinct
 * internal prefix permitted by the integration security chain) to match the
 * contract already coded in {@code FundoIntegrationClient}.
 *
 * <p>Tenant is resolved from the companion {@code X-Tenant-ID} trust header the
 * caller supplies; the response is a compact, read-only readiness summary.
 */
@RestController
@RequestMapping("/v1/internal/fundo")
public class FundoWorkforceReadinessController {

    private final FundoWorkforceReadinessService readinessService;
    private final FundoTrainingGateService trainingGateService;

    public FundoWorkforceReadinessController(
            FundoWorkforceReadinessService readinessService,
            FundoTrainingGateService trainingGateService) {
        this.readinessService = readinessService;
        this.trainingGateService = trainingGateService;
    }

    @GetMapping("/learners/{providerWorkerId}/cpd-summary")
    public ResponseEntity<Map<String, Object>> cpdSummary(
            @PathVariable String providerWorkerId,
            @RequestParam(name = "subjectType", defaultValue = "PROVIDER") String subjectType) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        if (tenantId == null) {
            return FundoV11Support.badRequest("TENANT_REQUIRED",
                    "X-Tenant-ID is required for training-evidence lookups");
        }
        return ResponseEntity.ok(
                readinessService.cpdSummary(tenantId, subjectType, providerWorkerId));
    }

    /**
     * Training-gate read-model consumed by Tshepo policy: is the subject's required
     * learning satisfied? {@code courseCodes} is a comma-separated list of native
     * Fundo course codes, each optionally suffixed with a graduated enforcement level
     * as {@code CODE:LEVEL} (e.g. {@code INFECT-CTL:HARD,HAND-HYG:ADVISORY}); an
     * unspecified level defaults to {@code ADVISORY}. Returns a per-requirement decision
     * (with its {@code enforcementLevel}), a graduated overall {@code decision}
     * (ALLOW / ADVISE / CONDITIONAL / BLOCK), and the {@code satisfied} flag.
     */
    @GetMapping("/learners/{subjectId}/training-gate")
    public ResponseEntity<Map<String, Object>> trainingGate(
            @PathVariable String subjectId,
            @RequestParam(name = "subjectType", defaultValue = "PROVIDER") String subjectType,
            @RequestParam(name = "courseCodes", required = false, defaultValue = "") String courseCodes) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        if (tenantId == null) {
            return FundoV11Support.badRequest("TENANT_REQUIRED",
                    "X-Tenant-ID is required for training-gate evaluation");
        }
        List<String> codes = courseCodes.isBlank()
                ? List.of()
                : Arrays.stream(courseCodes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return ResponseEntity.ok(
                trainingGateService.evaluate(tenantId, subjectType, subjectId, codes));
    }
}
