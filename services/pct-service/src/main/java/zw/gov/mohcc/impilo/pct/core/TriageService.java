package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TriageRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.TriageRecordRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records patient triage assessments and drives the journey to TRIAGED state.
 *
 * <p>Triage is the clinical assessment that determines a patient's acuity
 * level (1 = most critical, 5 = least urgent) upon arrival at a facility.
 * The acuity level is subsequently used by the {@link RoutingEngine} to
 * determine queue priority and by the {@link ControlTowerService} to
 * detect SLA breaches.</p>
 *
 * <p>Each triage record captures the acuity score, a JSON payload of vital
 * signs, and free-text clinical notes. Multiple triage records per journey
 * are permitted (e.g. re-triage after deterioration), with the most recent
 * record being authoritative.</p>
 */
@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    private final TriageRecordRepository triageRepository;
    private final JourneyRepository journeyRepository;
    private final JourneyStateMachine journeyStateMachine;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TriageService(TriageRecordRepository triageRepository,
                         JourneyRepository journeyRepository,
                         JourneyStateMachine journeyStateMachine,
                         EventOutboxRepository outboxRepository,
                         ObjectMapper objectMapper) {
        this.triageRepository = triageRepository;
        this.journeyRepository = journeyRepository;
        this.journeyStateMachine = journeyStateMachine;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Record a triage assessment for a patient journey.
     *
     * <p>Creates a {@link TriageRecordEntity} with the given acuity, vitals,
     * and notes. If the journey's current state permits it, the journey is
     * transitioned to {@link JourneyState#TRIAGED}. The triage record is
     * linked to the journey by {@code journeyId} and carries the identity
     * of the triaging clinician from the trust context.</p>
     *
     * @param journeyId  the patient journey being triaged
     * @param acuity     the triage acuity level (1 = most critical, 5 = least urgent)
     * @param vitalsJson a JSON object containing vital sign measurements
     * @param notes      free-text clinical notes; may be {@code null}
     * @return the created triage record
     * @throws IllegalArgumentException if the journey is not found
     * @throws IllegalArgumentException if acuity is outside the 1-5 range
     */
    @Transactional
    public TriageRecordEntity recordTriage(String journeyId, int acuity,
                                           String vitalsJson, String notes) {
        if (acuity < 1 || acuity > 5) {
            throw new IllegalArgumentException("Acuity must be between 1 and 5, got: " + acuity);
        }

        TrustContext ctx = TrustContextHolder.require();

        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Journey not found: " + journeyId));

        TriageRecordEntity record = new TriageRecordEntity();
        record.setJourneyId(journeyId);
        record.setTenantId(ctx.tenantId());
        record.setAcuity(acuity);
        record.setVitals(vitalsJson);
        record.setNotes(notes);
        record.setTriagedBy(ctx.actorId());
        record.setCreatedAt(OffsetDateTime.now());

        record = triageRepository.save(record);

        // Transition journey to TRIAGED
        journeyStateMachine.transition(journey, JourneyState.TRIAGED);

        // Outbox event for downstream consumers (e.g. BUTANO SHR, analytics)
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("journeyId", journeyId);
        payload.put("patientCpid", journey.getPatientCpid());
        payload.put("acuity", acuity);
        payload.put("triagedBy", ctx.actorId());
        payload.put("facilityId", journey.getFacilityId().toString());
        payload.put("recordedAt", record.getCreatedAt().toString());
        writeOutbox("TRIAGE", record.getId().toString(), "TRIAGE_RECORDED", toJson(payload));

        log.info("Triage recorded: journey={}, acuity={}, by={}",
                journeyId, acuity, ctx.actorId());

        return record;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void writeOutbox(String aggregateType, String aggregateId,
                             String eventType, String payloadJson) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        outbox.setTenantId(TrustContextHolder.require().tenantId());
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise outbox payload: {}", e.getMessage());
            return "{}";
        }
    }
}
