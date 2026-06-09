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
import zw.gov.mohcc.impilo.coverage.api.dto.CheckEligibilityRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.EligibilityCheckResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.EnrollmentEligibilityRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.EnrollmentEligibilityResponse;
import zw.gov.mohcc.impilo.coverage.core.CoverageEventService;
import zw.gov.mohcc.impilo.coverage.core.EnrollmentEligibilityService;
import zw.gov.mohcc.impilo.coverage.domain.EligibilityCheckEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.repository.EligibilityCheckRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Eligibility check endpoint (internal).
 *
 * Class B: bounded stale allowed. Stores decision_evidence_json with staleness evidence.
 */
@RestController
@RequestMapping("/internal/v1/coverage/eligibility")
public class EligibilityController {

    private final EligibilityCheckRepository eligibilityRepository;
    private final MemberCoverageRepository memberCoverageRepository;
    private final CoverageEventService eventService;
    private final EnrollmentEligibilityService enrollmentEligibilityService;

    public EligibilityController(EligibilityCheckRepository eligibilityRepository,
                                 MemberCoverageRepository memberCoverageRepository,
                                 CoverageEventService eventService,
                                 EnrollmentEligibilityService enrollmentEligibilityService) {
        this.eligibilityRepository = eligibilityRepository;
        this.memberCoverageRepository = memberCoverageRepository;
        this.eventService = eventService;
        this.enrollmentEligibilityService = enrollmentEligibilityService;
    }

    @PostMapping("/enrollment")
    public ResponseEntity<EnrollmentEligibilityResponse> checkEnrollmentEligibility(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody EnrollmentEligibilityRequest request) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(enrollmentEligibilityService.evaluate(tid, request.clientId(), request.planId()));
    }

    @GetMapping
    public ResponseEntity<List<EligibilityCheckResponse>> listForMember(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "member_cpid") String memberCpid) {
        UUID tid = UUID.fromString(tenantId);
        List<UUID> coverageIds = memberCoverageRepository.findByTenantIdAndClientId(tid, memberCpid).stream()
                .map(MemberCoverageEntity::getId)
                .toList();
        if (coverageIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(eligibilityRepository
                .findByTenantIdAndCoverageIdInOrderByCheckedAtDesc(tid, coverageIds)
                .stream()
                .map(this::toResponse)
                .toList());
    }

    @PostMapping({"", "/check"})
    @Transactional
    public ResponseEntity<EligibilityCheckResponse> checkEligibility(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @Valid @RequestBody CheckEligibilityRequest request) {

        UUID tid = UUID.fromString(tenantId);

        // Look up the coverage
        Optional<MemberCoverageEntity> coverageOpt =
                memberCoverageRepository.findByIdAndTenantId(request.coverageId(), tid);

        String resultCode;
        String resultMessage;
        if (coverageOpt.isEmpty()) {
            resultCode = "INELIGIBLE";
            resultMessage = "No active coverage found for the provided coverage ID";
        } else {
            MemberCoverageEntity coverage = coverageOpt.get();
            if (!"ACTIVE".equals(coverage.getStatus())) {
                resultCode = "INELIGIBLE";
                resultMessage = "Coverage is not active (status: " + coverage.getStatus() + ")";
            } else if (coverage.getEffectiveTo() != null
                    && coverage.getEffectiveTo().isBefore(LocalDate.now())) {
                resultCode = "INELIGIBLE";
                resultMessage = "Coverage has expired";
            } else {
                resultCode = "ELIGIBLE";
                resultMessage = "Member is eligible under the current coverage plan";
            }
        }

        // Build decision evidence (Class B staleness evidence)
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("consistency_class", "B");
        evidence.put("staleness_source", "local_db_projection");
        evidence.put("max_allowed_staleness_ms", 300000);
        evidence.put("actual_staleness_ms", 0);
        evidence.put("decision", resultCode);
        String evidenceJson;
        try {
            evidenceJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(evidence);
        } catch (Exception e) {
            evidenceJson = "{}";
        }

        EligibilityCheckEntity check = new EligibilityCheckEntity(
                tid, podId, request.coverageId(),
                request.patientRef(), request.serviceCode(),
                resultCode, resultMessage, evidenceJson);

        eligibilityRepository.save(check);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("coverage_id", request.coverageId().toString());
        payload.put("patient_ref", request.patientRef());
        payload.put("service_code", request.serviceCode());
        payload.put("result_code", resultCode);
        payload.put("result_message", resultMessage);
        UUID corr = CorrelationIds.fromHeader(correlationId);
        payload.put("meta", CoverageEventService.meta(corr));
        eventService.emitEligibilityChecked(check.getId(), corr, tid, podId,
                request.coverageId(), payload);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(check));
    }

    private EligibilityCheckResponse toResponse(EligibilityCheckEntity e) {
        return new EligibilityCheckResponse(
                e.getId(), e.getCoverageId(), e.getPatientRef(),
                e.getServiceCode(), e.getResultCode(), e.getResultMessage(),
                e.getCheckedAt());
    }

}
