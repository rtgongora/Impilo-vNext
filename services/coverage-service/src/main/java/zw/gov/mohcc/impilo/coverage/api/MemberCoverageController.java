package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.CoverageResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.CreateMemberCoverageRequest;
import zw.gov.mohcc.impilo.coverage.core.CoverageEventService;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/coverage/members")
public class MemberCoverageController {

    /** Membership statuses that count as an active enrolment for the duplicate guard (spec §10.2). */
    private static final java.util.Set<String> ACTIVE_ENROLMENT_STATES = java.util.Set.of(
            "DRAFT", "DECLARED", "PENDING_VERIFICATION", "VERIFIED", "ACTIVE", "GRACE_PERIOD");

    private final MemberCoverageRepository memberCoverageRepository;
    private final CoveragePlanRepository planRepository;
    private final CoverageEventService eventService;

    public MemberCoverageController(MemberCoverageRepository memberCoverageRepository,
                                    CoveragePlanRepository planRepository,
                                    CoverageEventService eventService) {
        this.memberCoverageRepository = memberCoverageRepository;
        this.planRepository = planRepository;
        this.eventService = eventService;
    }

    @GetMapping
    public ResponseEntity<List<CoverageResponse>> listByPlan(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "plan_id") UUID planId) {
        UUID tid = UUID.fromString(tenantId);
        List<CoverageResponse> rows = memberCoverageRepository.findByTenantIdAndPlanId(tid, planId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(rows);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<CoverageResponse> enrollMember(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @Valid @RequestBody CreateMemberCoverageRequest request) {
        UUID tid = UUID.fromString(tenantId);
        CoveragePlanEntity plan = planRepository.findById(request.planId())
                .filter(p -> p.getTenantId().equals(tid) && "ACTIVE".equals(p.getStatus()))
                .orElse(null);
        if (plan == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Duplicate-active-enrolment guard (spec §10; fixes the completion-lens nit where a
        // member could enrol twice on the same plan). A DB partial-unique index backs this.
        boolean alreadyActive = memberCoverageRepository
                .findByTenantIdAndClientId(tid, request.clientId()).stream()
                .anyMatch(m -> m.getPlanId().equals(request.planId())
                        && ACTIVE_ENROLMENT_STATES.contains(m.getStatus()));
        if (alreadyActive) {
            throw new IllegalStateException("Member already has an active enrolment on this plan");
        }

        String relationship = request.relationship() != null && !request.relationship().isBlank()
                ? request.relationship()
                : "SELF";
        String status = request.status() != null && !request.status().isBlank()
                ? request.status()
                : "ACTIVE";

        MemberCoverageEntity member = new MemberCoverageEntity(
                tid,
                podId,
                request.clientId(),
                request.planId(),
                request.memberNumber(),
                relationship,
                LocalDate.now());
        member.setStatus(status);
        memberCoverageRepository.save(member);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("client_id", member.getClientId());
        payload.put("plan_id", member.getPlanId().toString());
        payload.put("plan_code", plan.getPlanCode());
        payload.put("member_number", member.getMemberNumber());
        payload.put("status", member.getStatus());
        UUID corr = CorrelationIds.fromHeader(correlationId);
        payload.put("meta", CoverageEventService.meta(corr));
        eventService.emitMemberEnrolled(member.getId(), corr, tid, podId, member.getPlanId(), payload);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(member));
    }

    private CoverageResponse toResponse(MemberCoverageEntity mc) {
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
    }
}
