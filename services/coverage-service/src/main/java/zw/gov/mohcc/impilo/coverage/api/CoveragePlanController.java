package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.CoveragePlanResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.CoverageResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.CreateCoveragePlanRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.PatientBillingCategoryResponse;
import zw.gov.mohcc.impilo.coverage.core.CoverageEventService;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyEnrolmentRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Coverage plan management and coverage retrieval endpoint.
 *
 * Endpoints:
 * - GET  /internal/v1/coverage              — list active plans (internal)
 * - GET  /internal/v1/coverage/{id}         — get plan by ID (internal)
 * - POST /internal/v1/coverage              — create coverage plan (internal)
 * - GET  /internal/v1/coverage/member/{clientId} — retrieve member coverage (internal/external)
 */
@RestController
@RequestMapping("/internal/v1/coverage")
public class CoveragePlanController {

    private final CoveragePlanRepository planRepository;
    private final MemberCoverageRepository memberCoverageRepository;
    private final SubsidyEnrolmentRepository subsidyEnrolmentRepository;
    private final CoverageEventService eventService;

    public CoveragePlanController(CoveragePlanRepository planRepository,
                                  MemberCoverageRepository memberCoverageRepository,
                                  SubsidyEnrolmentRepository subsidyEnrolmentRepository,
                                  CoverageEventService eventService) {
        this.planRepository = planRepository;
        this.memberCoverageRepository = memberCoverageRepository;
        this.subsidyEnrolmentRepository = subsidyEnrolmentRepository;
        this.eventService = eventService;
    }

    /**
     * Member-facing plan rows: active member coverage joined to plan (same payload as /member/{id}).
     */
    @GetMapping("/plans")
    public ResponseEntity<List<CoverageResponse>> listPlansForMember(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "member_cpid") String memberCpid) {
        return getMemberCoverage(tenantId, memberCpid);
    }

    /**
     * Resolve a patient's billing category for downstream costing (COSTA charging rules), in
     * precedence order:
     * <ol>
     *   <li>an active subsidy enrolment's exemption category (e.g. INDIGENT, ELDERLY) — exemptions
     *       win, so waivers apply;</li>
     *   <li>otherwise the active coverage plan type (e.g. PRIVATE, PUBLIC);</li>
     *   <li>otherwise self-pay ({@code CASH}).</li>
     * </ol>
     * Keeps the billing classification owned by coverage-service rather than inferred in the
     * composition layer.
     */
    @GetMapping("/patient-category/{clientId}")
    public ResponseEntity<PatientBillingCategoryResponse> resolvePatientCategory(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String clientId) {
        UUID tid = UUID.fromString(tenantId);
        LocalDate today = LocalDate.now();

        // 1) Active exemption-carrying subsidy enrolment takes precedence — its exemption
        //    category drives waivers. Reads the consolidated cv_subsidy_enrolments SoR;
        //    the response source string "SUBSIDY_ENROLLMENT" is a wire contract (BFF
        //    teleconsult costing consumes it) and is kept as-is.
        var enrolment = subsidyEnrolmentRepository
                .findByTenantIdAndMemberCpidAndStatusAndExemptionCategoryIsNotNullOrderByCreatedAtDesc(
                        tid, clientId, "ACTIVE")
                .stream()
                .filter(e -> !e.getEffectiveFrom().isAfter(today)
                        && (e.getEffectiveTo() == null || !e.getEffectiveTo().isBefore(today)))
                .findFirst();
        if (enrolment.isPresent()) {
            return ResponseEntity.ok(new PatientBillingCategoryResponse(
                    clientId, enrolment.get().getExemptionCategory(), "SUBSIDY_ENROLLMENT", null));
        }

        // 2) Otherwise the active coverage plan type; 3) otherwise self-pay.
        return memberCoverageRepository
                .findByTenantIdAndClientIdAndStatus(tid, clientId, "ACTIVE")
                .stream()
                .findFirst()
                .map(mc -> {
                    CoveragePlanEntity plan = planRepository.findById(mc.getPlanId()).orElse(null);
                    String category = plan != null && plan.getPlanType() != null && !plan.getPlanType().isBlank()
                            ? plan.getPlanType().trim().toUpperCase(Locale.ROOT)
                            : "CASH";
                    String source = plan != null ? "COVERAGE_PLAN" : "DEFAULT_SELF_PAY";
                    String planCode = plan != null ? plan.getPlanCode() : null;
                    return ResponseEntity.ok(new PatientBillingCategoryResponse(clientId, category, source, planCode));
                })
                .orElseGet(() -> ResponseEntity.ok(
                        new PatientBillingCategoryResponse(clientId, "CASH", "DEFAULT_SELF_PAY", null)));
    }

    @GetMapping
    public ResponseEntity<List<CoveragePlanResponse>> listPlans(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<CoveragePlanResponse> plans = planRepository
                .findByTenantIdAndStatus(UUID.fromString(tenantId), "ACTIVE")
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CoveragePlanResponse> getPlan(@PathVariable UUID id) {
        return planRepository.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<CoveragePlanResponse> createPlan(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @Valid @RequestBody CreateCoveragePlanRequest request) {
        UUID tid = UUID.fromString(tenantId);
        CoveragePlanEntity plan = new CoveragePlanEntity(
                tid, podId,
                request.planCode(), request.planName(),
                request.payerId(), request.planType(),
                request.effectiveFrom());
        planRepository.save(plan);

        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("plan_code", plan.getPlanCode());
        payload.put("plan_name", plan.getPlanName());
        payload.put("payer_id", plan.getPayerId());
        payload.put("status", plan.getStatus());
        UUID corr = CorrelationIds.fromHeader(correlationId);
        payload.put("meta", CoverageEventService.meta(corr));
        eventService.emitPlanCreated(plan.getId(), corr, tid, podId, payload);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(plan));
    }

    /**
     * Coverage retrieval endpoint (internal/external as allowed).
     * Returns all active coverages for a given client.
     */
    @GetMapping("/member/{clientId}")
    public ResponseEntity<List<CoverageResponse>> getMemberCoverage(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String clientId) {
        UUID tid = UUID.fromString(tenantId);
        List<CoverageResponse> coverages = memberCoverageRepository
                .findByTenantIdAndClientIdAndStatus(tid, clientId, "ACTIVE")
                .stream()
                .map(mc -> {
                    CoveragePlanEntity plan = planRepository.findById(mc.getPlanId()).orElse(null);
                    return new CoverageResponse(
                            mc.getId(),
                            mc.getClientId(),
                            mc.getPlanId(),
                            plan != null ? plan.getPlanCode() : null,
                            plan != null ? plan.getPlanName() : null,
                            plan != null ? plan.getPayerId() : null,
                            mc.getMemberNumber(),
                            mc.getRelationship(),
                            mc.getStatus(),
                            mc.getEffectiveFrom(),
                            mc.getEffectiveTo());
                })
                .toList();
        return ResponseEntity.ok(coverages);
    }

    private CoveragePlanResponse toResponse(CoveragePlanEntity entity) {
        return new CoveragePlanResponse(
                entity.getId(),
                entity.getPlanCode(),
                entity.getPlanName(),
                entity.getPayerId(),
                entity.getPlanType(),
                entity.getStatus(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo());
    }

}
