package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.DeteriorationEscalationEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EarlyWarningScoreEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardPatientAlertEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.DeteriorationEscalationRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.WardPatientAlertRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the inpatient DETERIORATION ESCALATION lifecycle: an EWS at/above the configured threshold
 * raises an escalation with a response-due deadline; a clinician acknowledges/responds; if the deadline
 * passes unattended the escalation is OVERDUE and a Rito safety signal is emitted (Rito owns the safety
 * case — inpatient only links to it). Every state change emits an audit/outbox event.
 *
 * <p>Inpatient owns the escalation workflow; Rito owns the safety case. The link is a published event on
 * {@code inpatient.safety} (consumed by Rito's signal consumer), never a duplicated case record.</p>
 */
@Service
public class InpatientSafetyService {

    private static final Logger log = LoggerFactory.getLogger(InpatientSafetyService.class);

    private final DeteriorationEscalationRepository escalationRepository;
    private final WardPatientAlertRepository wardAlertRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final int escalationThreshold;
    private final int responseDueMinutes;

    public InpatientSafetyService(DeteriorationEscalationRepository escalationRepository,
                                  WardPatientAlertRepository wardAlertRepository,
                                  EventOutboxRepository outboxRepository,
                                  ObjectMapper objectMapper,
                                  @Value("${inpatient.ews.escalation-threshold:5}") int escalationThreshold,
                                  @Value("${inpatient.ews.response-due-minutes:15}") int responseDueMinutes) {
        this.escalationRepository = escalationRepository;
        this.wardAlertRepository = wardAlertRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.escalationThreshold = escalationThreshold;
        this.responseDueMinutes = responseDueMinutes;
    }

    public int escalationThreshold() {
        return escalationThreshold;
    }

    /**
     * Raise a deterioration escalation from an EWS that crossed the threshold. Creates the escalation
     * row with a response-due deadline, raises a ward alert for the bedside team, and emits the
     * {@code inpatient.ews.escalation_triggered} event. Returns the new escalation.
     */
    @Transactional
    public DeteriorationEscalationEntity raiseFromEws(EarlyWarningScoreEntity ews, UUID wardId, UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        DeteriorationEscalationEntity esc = new DeteriorationEscalationEntity();
        esc.setTenantId(ews.getTenantId());
        esc.setFacilityId(facilityId);
        esc.setWardId(wardId);
        esc.setSubjectCpid(ews.getSubjectCpid());
        esc.setAdmissionRef(ews.getAdmissionRef());
        esc.setEncounterId(ews.getEncounterId());
        esc.setScoreId(ews.getScoreId());
        esc.setTotalScore(ews.getTotalScore());
        esc.setRiskLevel(ews.getRiskLevel());
        esc.setStatus("RAISED");
        esc.setRaisedBy(ctx.actorId());
        esc.setRaisedAt(OffsetDateTime.now());
        esc.setResponseDueAt(OffsetDateTime.now().plusMinutes(responseDueMinutes));
        esc = escalationRepository.save(esc);

        // Ward alert so the bedside team sees it on the cockpit, even before any consumer reacts.
        WardPatientAlertEntity alert = new WardPatientAlertEntity();
        alert.setTenantId(ews.getTenantId());
        alert.setWardId(wardId);
        alert.setSubjectCpid(ews.getSubjectCpid());
        alert.setAlertType("DETERIORATION");
        alert.setMessage("EWS " + ews.getTotalScore() + " (" + ews.getRiskLevel()
                + ") — clinician response due by " + esc.getResponseDueAt());
        alert.setStatus("ACTIVE");
        wardAlertRepository.save(alert);

        Map<String, Object> payload = basePayload(esc);
        emit("ESCALATION", esc.getEscalationId().toString(),
                "inpatient.ews.escalation_triggered", esc.getTenantId(), payload);

        log.info("INPATIENT: deterioration escalation {} raised for {} (EWS {} {}), response due {}",
                esc.getEscalationId(), ews.getSubjectCpid(), ews.getTotalScore(), ews.getRiskLevel(),
                esc.getResponseDueAt());
        return esc;
    }

    @Transactional
    public DeteriorationEscalationEntity acknowledge(UUID escalationId) {
        DeteriorationEscalationEntity esc = require(escalationId);
        if ("CLOSED".equals(esc.getStatus()) || "RESPONDED".equals(esc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Escalation already resolved");
        }
        esc.setStatus("ACKNOWLEDGED");
        esc.setAcknowledgedBy(TrustContextHolder.require().actorId());
        esc.setAcknowledgedAt(OffsetDateTime.now());
        esc = escalationRepository.save(esc);
        emit("ESCALATION", esc.getEscalationId().toString(),
                "inpatient.ews.escalation_acknowledged", esc.getTenantId(), basePayload(esc));
        return esc;
    }

    @Transactional
    public DeteriorationEscalationEntity respond(UUID escalationId, String note) {
        DeteriorationEscalationEntity esc = require(escalationId);
        if ("CLOSED".equals(esc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Escalation already closed");
        }
        esc.setStatus("RESPONDED");
        esc.setRespondedBy(TrustContextHolder.require().actorId());
        esc.setRespondedAt(OffsetDateTime.now());
        esc.setResponseNote(note);
        esc = escalationRepository.save(esc);
        emit("ESCALATION", esc.getEscalationId().toString(),
                "inpatient.ews.escalation_responded", esc.getTenantId(), basePayload(esc));
        return esc;
    }

    public List<Map<String, Object>> listForPatient(String subjectCpid) {
        return escalationRepository
                .findByTenantIdAndSubjectCpidOrderByRaisedAtDesc(currentTenant(), subjectCpid)
                .stream().map(this::row).toList();
    }

    public List<Map<String, Object>> listOpenForWard(UUID wardId) {
        return escalationRepository
                .findByTenantIdAndWardIdAndStatusOrderByRaisedAtDesc(currentTenant(), wardId, "RAISED")
                .stream().map(this::row).toList();
    }

    /**
     * Scheduled sweep: any RAISED/ACKNOWLEDGED escalation whose response-due deadline has passed becomes
     * OVERDUE, and a Rito safety signal is emitted on {@code inpatient.safety} (Rito opens/links the case).
     * Idempotent — once moved to OVERDUE the row is no longer picked up.
     */
    @Scheduled(fixedDelayString = "${inpatient.ews.overdue-scan-ms:60000}")
    @Transactional
    public void sweepOverdue() {
        List<DeteriorationEscalationEntity> overdue = escalationRepository
                .findByStatusInAndResponseDueAtBefore(List.of("RAISED", "ACKNOWLEDGED"), OffsetDateTime.now());
        for (DeteriorationEscalationEntity esc : overdue) {
            markOverdueAndRaiseSafety(esc);
        }
        if (!overdue.isEmpty()) {
            log.warn("INPATIENT: {} deterioration escalation(s) overdue — Rito safety signals raised",
                    overdue.size());
        }
    }

    @Transactional
    public DeteriorationEscalationEntity markOverdueAndRaiseSafety(DeteriorationEscalationEntity esc) {
        esc.setStatus("OVERDUE");
        String ritoRef = "INPATIENT-EWS-" + esc.getEscalationId();
        esc.setRitoCaseRef(ritoRef);
        esc = escalationRepository.save(esc);

        // Rito-consumable safety signal (RitoSignalConsumer expects tenantId/facilityId/aggregateId/severity).
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("tenantId", esc.getTenantId().toString());
        safety.put("facilityId", esc.getFacilityId() != null ? esc.getFacilityId().toString() : null);
        safety.put("aggregateId", esc.getEscalationId().toString());
        safety.put("severity", esc.getTotalScore() >= 7 ? "HIGH" : "MEDIUM");
        safety.put("eventType", "inpatient.safety.event_raised");
        safety.put("category", "DETERIORATION_RESPONSE_DELAY");
        safety.put("subjectCpid", esc.getSubjectCpid());
        safety.put("wardId", esc.getWardId() != null ? esc.getWardId().toString() : null);
        safety.put("totalScore", esc.getTotalScore());
        safety.put("riskLevel", esc.getRiskLevel());
        safety.put("ritoCaseRef", ritoRef);
        safety.put("responseDueAt", esc.getResponseDueAt().toString());
        emit("SAFETY", esc.getEscalationId().toString(),
                "inpatient.safety.event_raised", esc.getTenantId(), safety);
        return esc;
    }

    // ── helpers ──────────────────────────────────────────────────────

    private DeteriorationEscalationEntity require(UUID id) {
        return escalationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Escalation not found"));
    }

    private UUID currentTenant() {
        return TrustContextHolder.require().tenantId();
    }

    private Map<String, Object> basePayload(DeteriorationEscalationEntity esc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("escalation_id", esc.getEscalationId().toString());
        m.put("tenant_id", esc.getTenantId().toString());
        m.put("subject_cpid", esc.getSubjectCpid());
        m.put("ward_id", esc.getWardId() != null ? esc.getWardId().toString() : null);
        m.put("score_id", esc.getScoreId() != null ? esc.getScoreId().toString() : null);
        m.put("total_score", esc.getTotalScore());
        m.put("risk_level", esc.getRiskLevel());
        m.put("status", esc.getStatus());
        m.put("response_due_at", esc.getResponseDueAt() != null ? esc.getResponseDueAt().toString() : null);
        return m;
    }

    private Map<String, Object> row(DeteriorationEscalationEntity esc) {
        Map<String, Object> m = basePayload(esc);
        m.put("raised_by", esc.getRaisedBy());
        m.put("raised_at", esc.getRaisedAt());
        m.put("acknowledged_by", esc.getAcknowledgedBy());
        m.put("responded_by", esc.getRespondedBy());
        m.put("rito_case_ref", esc.getRitoCaseRef());
        return m;
    }

    private void emit(String aggregateType, String aggregateId, String eventType, UUID tenantId,
                      Map<String, Object> payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setTenantId(tenantId.toString());
        outbox.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
        outbox.setCorrelationId(UUID.randomUUID().toString());
        outbox.setEventType(eventType);
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise escalation event", e);
        }
        outboxRepository.save(outbox);
    }
}
