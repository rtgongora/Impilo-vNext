package zw.gov.mohcc.impilo.coverage.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.domain.BenefitDefinitionEntity;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.repository.BenefitDefinitionRepository;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public (anonymous, no-auth) coverage plan-catalog read surface.
 *
 * <p>Purpose: let anyone compare published coverage plans and their benefit rules
 * <em>as reference/catalog data</em> — the same "care before coverage, help before
 * identity" gateway doctrine that governs the public facility directory. This lane
 * is <strong>reference-only</strong>: it NEVER exposes member, eligibility,
 * accumulator (used/reserved/remaining), or claims data. Those are member-bound and
 * live only behind the authenticated {@code /internal/v1/coverage} surface.</p>
 *
 * <p>Tenant: coverage reads are tenant-scoped. The authenticated controllers obtain
 * the tenant from the {@code X-Tenant-ID} header and call the repository with
 * {@code UUID.fromString(tenantId)}. This anonymous lane has no trust context, so it
 * pins the seeded care-plane tenant ({@link #CARE_PLANE_TENANT}) and calls the exact
 * same repository read methods that {@code CoveragePlanController.listPlans} and
 * {@code BenefitController.listBenefits} use.</p>
 *
 * <p>Response wrapping mirrors {@code CoveragePlanController}: raw
 * {@code ResponseEntity<List<...>>}, no {@code ApiResponse} envelope. Views are plain
 * {@link LinkedHashMap} field allow-lists so no member-bound accessor can leak.</p>
 *
 * Endpoints:
 * <ul>
 *   <li>GET /v1/public/coverage/plans — list ACTIVE coverage plans (reference view)</li>
 *   <li>GET /v1/public/coverage/plans/{planVersionId}/benefits — benefit rules for a plan version</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/public/coverage")
public class PublicPlanCatalogController {

    private static final Logger log = LoggerFactory.getLogger(PublicPlanCatalogController.class);

    /**
     * Seeded care-plane tenant. Coverage catalogue data is scoped to this tenant; the
     * anonymous lane pins it because it has no trust context to derive it from (matching
     * the way {@code CoveragePlanController} feeds a tenant UUID to the repository).
     */
    private static final UUID CARE_PLANE_TENANT =
            UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final CoveragePlanRepository planRepository;
    private final BenefitDefinitionRepository benefitRepository;

    public PublicPlanCatalogController(CoveragePlanRepository planRepository,
                                       BenefitDefinitionRepository benefitRepository) {
        this.planRepository = planRepository;
        this.benefitRepository = benefitRepository;
    }

    /**
     * List ACTIVE coverage plans as a reference/catalog view. Reuses the same read
     * ({@code findByTenantIdAndStatus(tenant, "ACTIVE")}) as
     * {@code CoveragePlanController.listPlans}, but returns an allow-listed public view.
     */
    @GetMapping("/plans")
    public ResponseEntity<List<Map<String, Object>>> listPlans() {
        try {
            List<Map<String, Object>> plans = planRepository
                    .findByTenantIdAndStatus(CARE_PLANE_TENANT, "ACTIVE")
                    .stream()
                    .map(PublicPlanCatalogController::toPublicPlanView)
                    .toList();
            return ResponseEntity.ok(plans);
        } catch (RuntimeException ex) {
            log.warn("Public plan catalog list failed: {}", ex.toString());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * List the benefit rules for a plan version as a reference/catalog view. Reuses the
     * same read ({@code findByTenantIdAndPlanVersionId}) as
     * {@code BenefitController.listBenefits}. Returns an allow-listed public view that
     * excludes any member-bound accumulator position (used/reserved/remaining).
     */
    @GetMapping("/plans/{planVersionId}/benefits")
    public ResponseEntity<List<Map<String, Object>>> listBenefits(@PathVariable UUID planVersionId) {
        try {
            List<BenefitDefinitionEntity> benefits =
                    benefitRepository.findByTenantIdAndPlanVersionId(CARE_PLANE_TENANT, planVersionId);
            if (benefits.isEmpty()) {
                // No published benefit rules for this plan version under the care-plane
                // tenant — treat as not found for the public catalog.
                return ResponseEntity.notFound().build();
            }
            List<Map<String, Object>> views = benefits.stream()
                    .map(PublicPlanCatalogController::toPublicBenefitView)
                    .toList();
            return ResponseEntity.ok(views);
        } catch (RuntimeException ex) {
            log.warn("Public benefit catalog list failed [planVersionId={}]: {}", planVersionId, ex.toString());
            return ResponseEntity.notFound().build();
        }
    }

    // ---- Redacted public views (field allow-lists) ----

    /**
     * Reference view of a coverage plan. Allow-list ONLY: planCode, planName, payerId
     * (opaque), planType, status, effectiveFrom, effectiveTo.
     */
    private static Map<String, Object> toPublicPlanView(CoveragePlanEntity plan) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("planCode", plan.getPlanCode());
        view.put("planName", plan.getPlanName());
        view.put("payerId", plan.getPayerId());
        view.put("planType", plan.getPlanType());
        view.put("status", plan.getStatus());
        view.put("effectiveFrom", plan.getEffectiveFrom());
        view.put("effectiveTo", plan.getEffectiveTo());
        return view;
    }

    /**
     * Reference view of a benefit rule. Allow-list ONLY: benefitCode, category,
     * description, coveragePercent, copayAmount, requiresAuthorisation, requiresReferral,
     * networkRequirement. Accumulator position fields (used/reserved/remaining) are
     * member-bound and deliberately excluded.
     */
    private static Map<String, Object> toPublicBenefitView(BenefitDefinitionEntity benefit) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("benefitCode", benefit.getBenefitCode());
        view.put("category", benefit.getCategory());
        view.put("description", benefit.getDescription());
        view.put("coveragePercent", benefit.getCoveragePercent());
        view.put("copayAmount", benefit.getCopayAmount());
        view.put("requiresAuthorisation", benefit.isRequiresAuthorisation());
        view.put("requiresReferral", benefit.isRequiresReferral());
        view.put("networkRequirement", benefit.getNetworkRequirement());
        return view;
    }
}
