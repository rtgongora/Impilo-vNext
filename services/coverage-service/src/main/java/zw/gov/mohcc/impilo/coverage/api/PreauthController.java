package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.CreatePreauthRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.PreauthDecisionRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.PreauthResponse;
import zw.gov.mohcc.impilo.coverage.core.CoverageEventService;
import zw.gov.mohcc.impilo.coverage.core.PreauthUtilizationService;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.domain.PreauthRequestEntity;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;
import zw.gov.mohcc.impilo.coverage.repository.PreauthRequestRepository;

/**
 * Pre-authorization creation and decision endpoint (internal).
 *
 * Stores decision_evidence_json for staleness evidence per v1.1 Law 6.
 */
@RestController
@RequestMapping("/internal/v1/coverage/preauth")
public class PreauthController {

    private final PreauthRequestRepository preauthRepository;
    private final CoverageEventService eventService;
    private final MemberCoverageRepository memberCoverageRepository;
    private final CoveragePlanRepository coveragePlanRepository;
    private final PreauthUtilizationService utilizationService;

    public PreauthController(
            PreauthRequestRepository preauthRepository,
            CoverageEventService eventService,
            MemberCoverageRepository memberCoverageRepository,
            CoveragePlanRepository coveragePlanRepository,
            PreauthUtilizationService utilizationService) {
        this.preauthRepository = preauthRepository;
        this.eventService = eventService;
        this.memberCoverageRepository = memberCoverageRepository;
        this.coveragePlanRepository = coveragePlanRepository;
        this.utilizationService = utilizationService;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<PreauthResponse> createPreauth(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @Valid @RequestBody CreatePreauthRequest request) {

        UUID tid = UUID.fromString(tenantId);
        UUID corr = CorrelationIds.fromHeader(correlationId);

        PreauthRequestEntity preauth = new PreauthRequestEntity(
                tid, podId, request.coverageId(),
                request.facilityId(), request.providerId(),
                request.requestType(), request.clinicalInfo(),
                request.requestedItems());
        if (request.quantityRequested() != null) {
            preauth.setQuantityRequested(request.quantityRequested());
        }
        if (request.annualLimit() != null) {
            preauth.setAnnualLimit(request.annualLimit());
        }
        if (request.approvalConditions() != null && !request.approvalConditions().isBlank()) {
            preauth.setApprovalConditions(request.approvalConditions());
        }
        if (request.utilizationPeriod() != null && !request.utilizationPeriod().isBlank()) {
            preauth.setUtilizationPeriod(request.utilizationPeriod());
        }

        preauthRepository.save(preauth);

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("coverage_id", request.coverageId().toString());
        payload.put("request_type", request.requestType());
        payload.put("status", "PENDING");
        payload.put("meta", CoverageEventService.meta(corr));
        eventService.emitPreauthRequested(preauth.getId(), corr, tid, podId,
                request.coverageId(), payload);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(preauth));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PreauthResponse> getPreauth(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        return preauthRepository.findByIdAndTenantId(id, UUID.fromString(tenantId))
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Decision endpoint — approve or deny a pre-authorization.
     * When approving, cumulative utilization limits are enforced when {@code annual_limit} is set.
     */
    @PutMapping("/{id}/decision")
    @Transactional
    public ResponseEntity<PreauthResponse> decidePreauth(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @PathVariable UUID id,
            @Valid @RequestBody PreauthDecisionRequest request) {

        UUID tid = UUID.fromString(tenantId);
        UUID corr = CorrelationIds.fromHeader(correlationId);

        PreauthRequestEntity preauth = preauthRepository.findByIdAndTenantId(id, tid)
                .orElse(null);
        if (preauth == null) {
            return ResponseEntity.notFound().build();
        }

        if (!"PENDING".equals(preauth.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        if ("APPROVED".equals(request.status())) {
            MemberCoverageEntity member = memberCoverageRepository
                    .findByIdAndTenantId(preauth.getCoverageId(), tid)
                    .orElse(null);
            CoveragePlanEntity plan = member != null
                    ? coveragePlanRepository.findById(member.getPlanId()).orElse(null)
                    : null;
            String planCode = plan != null ? plan.getPlanCode() : "UNKNOWN";
            BigDecimal qtyApproved = request.quantityApproved() != null
                    ? request.quantityApproved()
                    : (preauth.getQuantityRequested() != null
                            ? preauth.getQuantityRequested()
                            : BigDecimal.ONE);
            if (member != null && plan != null) {
                Optional<String> denial = utilizationService.evaluateApproval(
                        preauth, member, planCode, qtyApproved);
                if (denial.isPresent()) {
                    preauth.decide("DENIED", denial.get(), request.decisionEvidenceJson());
                    preauthRepository.save(preauth);
                    LinkedHashMap<String, Object> denyPayload = new LinkedHashMap<>();
                    denyPayload.put("old_status", "PENDING");
                    denyPayload.put("new_status", "DENIED");
                    denyPayload.put("reason", "UTILIZATION_LIMIT_EXCEEDED");
                    denyPayload.put("meta", CoverageEventService.meta(corr));
                    eventService.emitPreauthDenied(preauth.getId(), corr, tid, podId,
                            preauth.getCoverageId(), denyPayload);
                    return ResponseEntity.ok(toResponse(preauth));
                }
            }
            preauth.setQuantityApproved(qtyApproved);
            preauth.decide("APPROVED", request.decisionJson(), request.decisionEvidenceJson());
            preauthRepository.save(preauth);
            if (member != null && plan != null && preauth.getAnnualLimit() != null) {
                utilizationService.recordApprovedUsage(preauth, member, planCode, qtyApproved);
            }
            LinkedHashMap<String, Object> approvePayload = new LinkedHashMap<>();
            approvePayload.put("old_status", "PENDING");
            approvePayload.put("new_status", "APPROVED");
            approvePayload.put("meta", CoverageEventService.meta(corr));
            eventService.emitPreauthApproved(preauth.getId(), corr, tid, podId,
                    preauth.getCoverageId(), approvePayload);
            return ResponseEntity.ok(toResponse(preauth));
        }

        preauth.decide(request.status(), request.decisionJson(), request.decisionEvidenceJson());
        preauthRepository.save(preauth);
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("old_status", "PENDING");
        payload.put("new_status", preauth.getStatus());
        payload.put("meta", CoverageEventService.meta(corr));
        if ("APPROVED".equals(preauth.getStatus())) {
            eventService.emitPreauthApproved(preauth.getId(), corr, tid, podId,
                    preauth.getCoverageId(), payload);
        } else {
            eventService.emitPreauthDenied(preauth.getId(), corr, tid, podId,
                    preauth.getCoverageId(), payload);
        }

        return ResponseEntity.ok(toResponse(preauth));
    }

    private PreauthResponse toResponse(PreauthRequestEntity e) {
        return new PreauthResponse(
                e.getId(), e.getCoverageId(), e.getFacilityId(),
                e.getProviderId(), e.getRequestType(), e.getStatus(),
                e.getRequestedItems(), e.getDecisionJson(),
                e.getRequestedAt(), e.getDecidedAt(), e.getExpiresAt(),
                e.getQuantityRequested(), e.getQuantityApproved(), e.getQuantityConsumed(), e.getAnnualLimit());
    }

}
