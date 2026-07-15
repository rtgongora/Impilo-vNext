package zw.gov.mohcc.impilo.costa.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.domain.entity.InsurancePlanEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.InsurancePlanRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Insurance-plan terms governance.
 *
 * <p>When a coverage plan is first seen, COSTA saves a {@code PENDING_TERMS}
 * placeholder — it never invents the billing split. Finance configures the real
 * terms (coverage %, co-pay, deductible, annual cap) here, which transitions the
 * plan to {@code ACTIVE} so the coverage engine can start splitting bills against
 * it. Terms are always finance-entered, never derived.</p>
 */
@RestController
@RequestMapping("/costa/v1/insurance-plans")
public class InsurancePlanController {

    private static final Logger log = LoggerFactory.getLogger(InsurancePlanController.class);

    static final String STATUS_PENDING_TERMS = "PENDING_TERMS";
    static final String STATUS_ACTIVE = "ACTIVE";

    private final InsurancePlanRepository repository;

    public InsurancePlanController(InsurancePlanRepository repository) {
        this.repository = repository;
    }

    /** Finance-entered plan terms. All splits are supplied, never invented. */
    public record PlanTermsRequest(
            BigDecimal coveragePct,
            BigDecimal copayPct,
            BigDecimal copayFixed,
            BigDecimal deductible,
            BigDecimal annualCap,
            String planName) {
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InsurancePlanEntity>>> list(
            @RequestParam(value = "status", required = false) String status) {
        var ctx = TrustContextHolder.require();
        List<InsurancePlanEntity> plans = (status != null && !status.isBlank())
                ? repository.findByTenantIdAndStatus(ctx.tenantId(), status)
                : repository.findByTenantIdOrderByCreatedAtDesc(ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.ok(plans, ctx.correlationId().toString()));
    }

    /**
     * Configure a plan's terms and activate it. Targets the PENDING_TERMS row
     * if present, otherwise an existing ACTIVE row (re-configuration). Coverage
     * and co-pay percentages must be supplied and each fall in [0, 100].
     */
    @PutMapping("/{planCode}")
    public ResponseEntity<ApiResponse<InsurancePlanEntity>> configure(
            @PathVariable String planCode,
            @RequestBody PlanTermsRequest req) {
        var ctx = TrustContextHolder.require();
        String cid = ctx.correlationId().toString();

        if (req == null || req.coveragePct() == null || req.copayPct() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "TERMS_REQUIRED",
                    "coveragePct and copayPct are required — plan terms are finance-entered, never derived",
                    400, cid));
        }
        if (outOfRange(req.coveragePct()) || outOfRange(req.copayPct())) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "TERMS_OUT_OF_RANGE", "coveragePct and copayPct must be between 0 and 100", 400, cid));
        }

        List<InsurancePlanEntity> matches = repository.findByTenantIdAndPlanCode(ctx.tenantId(), planCode);
        // Prefer the PENDING_TERMS placeholder; fall back to an ACTIVE row for re-config.
        Optional<InsurancePlanEntity> target = matches.stream()
                .filter(p -> STATUS_PENDING_TERMS.equals(p.getStatus()))
                .findFirst()
                .or(() -> matches.stream()
                        .filter(p -> STATUS_ACTIVE.equals(p.getStatus()))
                        .max(Comparator.comparing(InsurancePlanEntity::getCreatedAt)));
        if (target.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error(
                    "PLAN_NOT_FOUND", "No PENDING_TERMS or ACTIVE plan for code " + planCode, 404, cid));
        }

        InsurancePlanEntity plan = target.get();
        String priorStatus = plan.getStatus();
        plan.setCoveragePct(req.coveragePct());
        plan.setCopayPct(req.copayPct());
        plan.setCopayFixed(req.copayFixed());
        if (req.deductible() != null) plan.setDeductible(req.deductible());
        plan.setAnnualCap(req.annualCap());
        if (req.planName() != null && !req.planName().isBlank()) plan.setPlanName(req.planName());
        plan.setStatus(STATUS_ACTIVE);
        InsurancePlanEntity saved = repository.save(plan);

        log.info("Insurance plan {} ({}) configured {}→ACTIVE by {} (coverage={}%, copay={}%)",
                planCode, saved.getId(), priorStatus, ctx.actorId(), req.coveragePct(), req.copayPct());
        return ResponseEntity.ok(ApiResponse.ok(saved, cid));
    }

    private static boolean outOfRange(BigDecimal pct) {
        return pct.signum() < 0 || pct.compareTo(new BigDecimal("100")) > 0;
    }
}
