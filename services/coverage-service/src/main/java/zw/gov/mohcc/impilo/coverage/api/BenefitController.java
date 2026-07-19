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
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.BenefitDtos.BenefitPositionResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.BenefitDtos.BenefitResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.BenefitDtos.CreateBenefitRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.BenefitDtos.MovementRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.BenefitDtos.MovementResponse;
import zw.gov.mohcc.impilo.coverage.core.BenefitAccumulatorService;
import zw.gov.mohcc.impilo.coverage.core.BenefitAccumulatorService.MovementResult;
import zw.gov.mohcc.impilo.coverage.domain.BenefitDefinitionEntity;
import zw.gov.mohcc.impilo.coverage.domain.BenefitLimitEntity;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.domain.PlanVersionEntity;
import zw.gov.mohcc.impilo.coverage.repository.BenefitDefinitionRepository;
import zw.gov.mohcc.impilo.coverage.repository.BenefitLimitRepository;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;
import zw.gov.mohcc.impilo.coverage.repository.PlanVersionRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Benefit configuration + reservation-aware accumulator endpoints (spec §13).
 */
@RestController
@RequestMapping("/internal/v1/coverage")
public class BenefitController {

    private final BenefitDefinitionRepository benefitRepository;
    private final BenefitLimitRepository limitRepository;
    private final PlanVersionRepository planVersionRepository;
    private final MemberCoverageRepository memberRepository;
    private final CoveragePlanRepository planRepository;
    private final BenefitAccumulatorService accumulatorService;

    public BenefitController(BenefitDefinitionRepository benefitRepository,
                             BenefitLimitRepository limitRepository,
                             PlanVersionRepository planVersionRepository,
                             MemberCoverageRepository memberRepository,
                             CoveragePlanRepository planRepository,
                             BenefitAccumulatorService accumulatorService) {
        this.benefitRepository = benefitRepository;
        this.limitRepository = limitRepository;
        this.planVersionRepository = planVersionRepository;
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.accumulatorService = accumulatorService;
    }

    @GetMapping("/plan-versions/{planVersionId}/benefits")
    public ResponseEntity<List<BenefitResponse>> listBenefits(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID planVersionId) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(benefitRepository.findByTenantIdAndPlanVersionId(tid, planVersionId)
                .stream().map(BenefitResponse::of).toList());
    }

    @PostMapping("/plan-versions/{planVersionId}/benefits")
    @Transactional
    public ResponseEntity<BenefitResponse> createBenefit(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @PathVariable UUID planVersionId,
            @Valid @RequestBody CreateBenefitRequest req) {
        UUID tid = UUID.fromString(tenantId);
        PlanVersionEntity version = planVersionRepository.findByIdAndTenantId(planVersionId, tid)
                .orElseThrow(() -> new IllegalArgumentException("Plan version not found: " + planVersionId));
        BenefitDefinitionEntity b = BenefitDefinitionEntity.create(tid, podId, version.getId(),
                req.benefitCode().trim(), req.category().trim(), req.description());
        if (req.coveragePercent() != null) b.setCoveragePercent(req.coveragePercent());
        if (req.copayAmount() != null) b.setCopayAmount(req.copayAmount());
        if (req.requiresAuthorisation() != null) b.setRequiresAuthorisation(req.requiresAuthorisation());
        if (req.requiresReferral() != null) b.setRequiresReferral(req.requiresReferral());
        if (req.networkRequirement() != null) b.setNetworkRequirement(req.networkRequirement());
        b.setCurrency(version.getCurrency());
        benefitRepository.save(b);
        if (req.annualLimit() != null) {
            limitRepository.save(BenefitLimitEntity.create(tid, b.getId(), "MONETARY",
                    req.annualLimit(), "CALENDAR_YEAR"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(BenefitResponse.of(b));
    }

    @GetMapping("/members/{memberId}/benefit-accumulators")
    public ResponseEntity<List<BenefitPositionResponse>> accumulators(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID memberId) {
        UUID tid = UUID.fromString(tenantId);
        MemberCoverageEntity member = memberRepository.findByIdAndTenantId(memberId, tid)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found: " + memberId));
        UUID planVersionId = resolvePlanVersion(member);
        if (planVersionId == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(accumulatorService.positionsForMember(tid, memberId, planVersionId)
                .stream().map(BenefitPositionResponse::of).toList());
    }

    @PostMapping("/members/{memberId}/benefits/reserve")
    @Transactional
    public ResponseEntity<MovementResponse> reserve(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID memberId,
            @Valid @RequestBody MovementRequest req) {
        MovementResult r = accumulatorService.reserve(UUID.fromString(tenantId), podId, memberId,
                req.benefitCode(), req.amount(), req.reference(), CorrelationIds.fromHeader(correlationId));
        return respond(r);
    }

    @PostMapping("/members/{memberId}/benefits/consume")
    @Transactional
    public ResponseEntity<MovementResponse> consume(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID memberId,
            @Valid @RequestBody MovementRequest req) {
        MovementResult r = accumulatorService.consume(UUID.fromString(tenantId), podId, memberId,
                req.benefitCode(), req.amount(), req.reference(), CorrelationIds.fromHeader(correlationId));
        return respond(r);
    }

    @PostMapping("/members/{memberId}/benefits/release")
    @Transactional
    public ResponseEntity<MovementResponse> release(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID memberId,
            @Valid @RequestBody MovementRequest req) {
        MovementResult r = accumulatorService.release(UUID.fromString(tenantId), podId, memberId,
                req.benefitCode(), req.amount(), req.reference(), CorrelationIds.fromHeader(correlationId));
        return respond(r);
    }

    private ResponseEntity<MovementResponse> respond(MovementResult r) {
        // A rejected movement (limit exceeded / insufficient reserved) is an honest 409, not a fake 200.
        HttpStatus status = r.applied() ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(MovementResponse.of(r));
    }

    private UUID resolvePlanVersion(MemberCoverageEntity member) {
        CoveragePlanEntity plan = planRepository.findById(member.getPlanId()).orElse(null);
        return plan != null ? plan.getPlanVersionId() : null;
    }
}
