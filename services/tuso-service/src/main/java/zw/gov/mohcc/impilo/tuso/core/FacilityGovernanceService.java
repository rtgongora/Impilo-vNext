package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityGovernanceCaseEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityGovernanceCaseRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Facility governance journeys (FJ6/FJ7/FJ9). Low-risk facility fields are
 * self-service and never come here; high-risk fields, transfers and duplicate
 * reports open a steward-decided case. Transfer retains the SAME facility
 * identity; a merge re-points onto {@code merged_into_id} via
 * {@code FacilityMergeService}-style logic (deferred to the merge wave) — this
 * service records the decision and never mutates authoritative fields
 * self-service.
 */
@Service
public class FacilityGovernanceService {

    /**
     * Fields whose change requires a governed case (FJ6). Everything else
     * (public phone/email/opening hours/directions/accessibility) is low-risk
     * self-service and handled by the existing update controllers.
     */
    public static final Set<String> HIGH_RISK_FIELDS = Set.of(
            "name", "ownership", "level", "facilityType", "latitude", "longitude",
            "legalStatus", "practitionerInCharge", "licence", "regulatoryStatus", "closure");

    public static final String EVENT_OPENED = "tuso.facility.governance_case.opened";
    public static final String EVENT_DECIDED = "tuso.facility.governance_case.decided";

    private static final Logger log = LoggerFactory.getLogger(FacilityGovernanceService.class);

    private final FacilityRepository facilityRepository;
    private final FacilityGovernanceCaseRepository caseRepository;
    private final zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FacilityGovernanceService(
            FacilityRepository facilityRepository,
            FacilityGovernanceCaseRepository caseRepository,
            zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.facilityRepository = facilityRepository;
        this.caseRepository = caseRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /** Classify a field-change request: which fields are high-risk (need a case)? */
    public static Set<String> highRiskFieldsIn(Map<String, Object> changes) {
        java.util.Set<String> found = new java.util.LinkedHashSet<>();
        if (changes != null) {
            for (String key : changes.keySet()) {
                if (HIGH_RISK_FIELDS.contains(key)) {
                    found.add(key);
                }
            }
        }
        return found;
    }

    @Transactional
    public FacilityGovernanceCaseEntity openHighRiskUpdate(UUID facilityUuid, Map<String, Object> changes) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());
        Set<String> highRisk = highRiskFieldsIn(changes);
        if (highRisk.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No high-risk fields in the request — low-risk changes are self-service.");
        }
        return openCase(ctx, facility, FacilityGovernanceCaseEntity.TYPE_HIGH_RISK_UPDATE,
                Map.of("changes", changes, "highRiskFields", highRisk));
    }

    @Transactional
    public FacilityGovernanceCaseEntity openTransfer(UUID facilityUuid, Map<String, Object> transfer) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());
        if (transfer == null || transfer.get("incomingPartyHealthId") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A transfer needs the incoming party.");
        }
        return openCase(ctx, facility, FacilityGovernanceCaseEntity.TYPE_TRANSFER, transfer);
    }

    /** FJ9: anyone may report a duplicate/incorrect/fake facility. */
    @Transactional
    public FacilityGovernanceCaseEntity reportDuplicate(UUID facilityUuid, Map<String, Object> report) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());
        return openCase(ctx, facility, FacilityGovernanceCaseEntity.TYPE_DUPLICATE_REPORT,
                report != null ? report : Map.of());
    }

    /**
     * Steward decision. TRANSFER approval retains the SAME facility identity —
     * the facility_uuid never changes; only the administering relationship
     * moves (recorded via the appointment rail + org-registry, not here). A
     * DUPLICATE_REPORT resolves to MERGED / REDIRECTED / REJECTED; the actual
     * alias/anchor re-pointing is the merge wave's job.
     */
    @Transactional
    public FacilityGovernanceCaseEntity decide(Long caseId, String decision, String note) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityGovernanceCaseEntity governanceCase = caseRepository
                .findByIdAndTenantId(caseId, ctx.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Governance case not found: " + caseId));
        if (!FacilityGovernanceCaseEntity.STATUS_OPEN.equals(governanceCase.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Governance case is already decided: " + governanceCase.getStatus());
        }
        String target = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        Set<String> allowed = switch (governanceCase.getCaseType()) {
            case FacilityGovernanceCaseEntity.TYPE_DUPLICATE_REPORT -> Set.of(
                    FacilityGovernanceCaseEntity.STATUS_MERGED,
                    FacilityGovernanceCaseEntity.STATUS_REDIRECTED,
                    FacilityGovernanceCaseEntity.STATUS_REJECTED);
            default -> Set.of(
                    FacilityGovernanceCaseEntity.STATUS_APPROVED,
                    FacilityGovernanceCaseEntity.STATUS_REJECTED);
        };
        if (!allowed.contains(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Decision " + decision + " is not valid for a " + governanceCase.getCaseType() + " case.");
        }

        governanceCase.setStatus(target);
        governanceCase.setDecidedBy(ctx.actorId());
        governanceCase.setDecidedAt(Instant.now());
        governanceCase.setDecisionNote(note);
        governanceCase = caseRepository.save(governanceCase);
        publish(EVENT_DECIDED, ctx, governanceCase);
        log.info("facility governance case {} ({}) decided {} by {}",
                governanceCase.getCaseRef(), governanceCase.getCaseType(), target, ctx.actorId());
        return governanceCase;
    }

    @Transactional(readOnly = true)
    public List<FacilityGovernanceCaseEntity> listOpen(String caseType) {
        TrustContext ctx = TrustContextHolder.require();
        if (caseType == null || caseType.isBlank()) {
            return caseRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(
                    ctx.tenantId(), FacilityGovernanceCaseEntity.STATUS_OPEN);
        }
        String normalized = caseType.trim().toUpperCase(Locale.ROOT);
        if (!FacilityGovernanceCaseEntity.TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown case type: " + caseType);
        }
        return caseRepository.findByTenantIdAndCaseTypeAndStatusOrderByCreatedAtDesc(
                ctx.tenantId(), normalized, FacilityGovernanceCaseEntity.STATUS_OPEN);
    }

    private FacilityGovernanceCaseEntity openCase(TrustContext ctx, FacilityEntity facility,
                                                  String type, Map<String, Object> payload) {
        FacilityGovernanceCaseEntity governanceCase = new FacilityGovernanceCaseEntity();
        governanceCase.setTenantId(ctx.tenantId());
        governanceCase.setCaseRef("FGC-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        governanceCase.setCaseType(type);
        governanceCase.setFacilityUuid(facility.getFacilityUuid());
        governanceCase.setRaisedBy(ctx.actorId());
        governanceCase.setPayload(toJson(payload));
        governanceCase = caseRepository.save(governanceCase);
        publish(EVENT_OPENED, ctx, governanceCase);
        log.info("facility governance case {} ({}) opened for facility {} by {}",
                governanceCase.getCaseRef(), type, facility.getFacilityUuid(), ctx.actorId());
        return governanceCase;
    }

    private FacilityEntity requireFacility(UUID facilityUuid, UUID tenantId) {
        FacilityEntity facility = facilityRepository.findByFacilityUuid(facilityUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Facility not found: " + facilityUuid));
        if (!facility.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation for facility: " + facilityUuid);
        }
        return facility;
    }

    private void publish(String eventType, TrustContext ctx, FacilityGovernanceCaseEntity c) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseRef", c.getCaseRef());
        payload.put("caseType", c.getCaseType());
        payload.put("facilityUuid", c.getFacilityUuid().toString());
        payload.put("status", c.getStatus());

        String correlationId = ctx.correlationId() != null ? ctx.correlationId().toString() : null;
        zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity outbox =
                new zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity();
        outbox.setAggregateType("FACILITY_GOVERNANCE_CASE");
        outbox.setAggregateId(c.getCaseRef());
        outbox.setEventType(eventType);
        outbox.setPayload(payload);
        outbox.setTenantId(ctx.tenantId() != null ? ctx.tenantId().toString() : null);
        outbox.setCorrelationId(correlationId);
        outbox.setCausationId(correlationId);
        outbox.setProducer("tuso");
        outbox.setSubjectType("FacilityGovernanceCase");
        outbox.setSubjectId(c.getCaseRef());
        outbox.setPartitionKey(c.getFacilityUuid().toString());
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(Instant.now().atOffset(java.time.ZoneOffset.UTC));
        outboxRepository.save(outbox);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise governance payload", e);
        }
    }
}
