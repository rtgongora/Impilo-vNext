package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeathCaseEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.DeathCaseRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the in-facility death recording and UBOMI (civil registration)
 * notification workflow.
 *
 * <p>When a patient death is pronounced in a facility, this workflow:</p>
 * <ol>
 *   <li>Records the death with the pronouncing clinician and timestamp</li>
 *   <li>Transitions the journey to {@link JourneyState#DEATH_RECORDED} (terminal)</li>
 *   <li>Tracks UBOMI civil registration notification status</li>
 *   <li>Tracks attachment of the death certificate document</li>
 * </ol>
 *
 * <p>The UBOMI (Zimbabwe Civil Registration and Vital Statistics) integration
 * ensures that facility-level deaths are reported to the national CRVS
 * system. The {@code ubomiNotificationId} and {@code ubomiStatus} fields
 * track the lifecycle of this notification.</p>
 */
@Service
public class DeathWorkflow {

    private static final Logger log = LoggerFactory.getLogger(DeathWorkflow.class);

    private final DeathCaseRepository deathCaseRepository;
    private final JourneyRepository journeyRepository;
    private final JourneyStateMachine journeyStateMachine;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DeathWorkflow(DeathCaseRepository deathCaseRepository,
                         JourneyRepository journeyRepository,
                         JourneyStateMachine journeyStateMachine,
                         EventOutboxRepository outboxRepository,
                         ObjectMapper objectMapper) {
        this.deathCaseRepository = deathCaseRepository;
        this.journeyRepository = journeyRepository;
        this.journeyStateMachine = journeyStateMachine;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Record a patient death during their facility journey.
     *
     * <p>Creates a death case entity and transitions the journey to
     * {@link JourneyState#DEATH_RECORDED}. This is a terminal journey
     * state — no further state transitions are possible.</p>
     *
     * @param journeyId    the patient journey
     * @param pronouncedBy the identifier or name of the clinician who pronounced death
     * @param pronouncedAt the date/time of death pronouncement
     * @return the created death case entity
     * @throws IllegalArgumentException if the journey is not found
     */
    @Transactional
    public DeathCaseEntity recordDeath(String journeyId, String pronouncedBy,
                                       OffsetDateTime pronouncedAt) {
        TrustContext ctx = TrustContextHolder.require();

        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Journey not found: " + journeyId));

        DeathCaseEntity deathCase = new DeathCaseEntity();
        deathCase.setId(UUID.randomUUID());
        deathCase.setJourneyId(journeyId);
        deathCase.setTenantId(ctx.tenantId());
        deathCase.setFacilityId(journey.getFacilityId());
        deathCase.setPatientCpid(journey.getPatientCpid());
        deathCase.setPronouncedBy(pronouncedBy);
        deathCase.setPronouncedAt(pronouncedAt);
        deathCase.setStatus("RECORDED");
        deathCase.setCreatedAt(OffsetDateTime.now());

        deathCase = deathCaseRepository.save(deathCase);

        // Transition journey to terminal DEATH_RECORDED state
        journeyStateMachine.transition(journey, JourneyState.DEATH_RECORDED);

        // Outbox event for UBOMI notification service and analytics
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", deathCase.getId().toString());
        payload.put("journeyId", journeyId);
        payload.put("patientCpid", journey.getPatientCpid());
        payload.put("facilityId", journey.getFacilityId().toString());
        payload.put("pronouncedBy", pronouncedBy);
        payload.put("pronouncedAt", pronouncedAt.toString());
        writeOutbox("DEATH_CASE", deathCase.getId().toString(),
                "DEATH_RECORDED", toJson(payload));

        log.info("Death recorded: case={}, journey={}, pronounced by {} at {}",
                deathCase.getId(), journeyId, pronouncedBy, pronouncedAt);

        return deathCase;
    }

    /**
     * Update the UBOMI (civil registration) notification status for a death case.
     *
     * <p>Called by the integration layer when UBOMI acknowledges or updates
     * the death notification. Tracks the external notification identifier
     * and its current status in the CRVS system.</p>
     *
     * @param caseId              the death case to update
     * @param ubomiNotificationId the UBOMI-assigned notification reference
     * @param ubomiStatus         the current UBOMI processing status (e.g. SUBMITTED, ACKNOWLEDGED, REGISTERED)
     * @return the updated death case entity
     * @throws IllegalArgumentException if the case is not found
     */
    @Transactional
    public DeathCaseEntity updateUbomiStatus(UUID caseId, String ubomiNotificationId,
                                              String ubomiStatus) {
        DeathCaseEntity deathCase = deathCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Death case not found: " + caseId));

        deathCase.setUbomiNotificationId(ubomiNotificationId);
        deathCase.setUbomiStatus(ubomiStatus);

        deathCase = deathCaseRepository.save(deathCase);

        log.info("UBOMI status updated: case={}, notificationId={}, status={}",
                caseId, ubomiNotificationId, ubomiStatus);

        return deathCase;
    }

    /**
     * Attach a death certificate document reference to a death case.
     *
     * <p>The certificate document is stored in the Document Service (MinIO/S3)
     * and referenced here by its document identifier for cross-service
     * traceability.</p>
     *
     * @param caseId    the death case
     * @param certDocId the document identifier from the Document Service
     * @return the updated death case entity
     * @throws IllegalArgumentException if the case is not found
     */
    @Transactional
    public DeathCaseEntity attachCertificate(UUID caseId, String certDocId) {
        DeathCaseEntity deathCase = deathCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Death case not found: " + caseId));

        deathCase.setCertDocId(certDocId);

        deathCase = deathCaseRepository.save(deathCase);

        log.info("Death certificate attached: case={}, docId={}", caseId, certDocId);

        return deathCase;
    }

    /**
     * Close the death case workflow.
     *
     * <p>Sets the {@code closedAt} timestamp and publishes a completion
     * event for downstream consumers (analytics, registry updates).</p>
     *
     * @param caseId the death case to close
     * @return the closed death case entity
     * @throws IllegalArgumentException if the case is not found
     * @throws IllegalStateException    if the case is not in RECORDED status
     */
    @Transactional
    public DeathCaseEntity completeDeath(UUID caseId) {
        DeathCaseEntity deathCase = deathCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Death case not found: " + caseId));

        if (!"RECORDED".equals(deathCase.getStatus())) {
            throw new IllegalStateException(
                    "Cannot complete death case in status: " + deathCase.getStatus());
        }

        deathCase.setStatus("COMPLETED");
        deathCase.setClosedAt(OffsetDateTime.now());

        deathCase = deathCaseRepository.save(deathCase);

        // Outbox event
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", caseId.toString());
        payload.put("journeyId", deathCase.getJourneyId());
        payload.put("patientCpid", deathCase.getPatientCpid());
        payload.put("closedAt", deathCase.getClosedAt().toString());
        payload.put("ubomiNotificationId", deathCase.getUbomiNotificationId());
        payload.put("ubomiStatus", deathCase.getUbomiStatus());
        payload.put("certDocId", deathCase.getCertDocId());
        writeOutbox("DEATH_CASE", caseId.toString(),
                "DEATH_CASE_COMPLETED", toJson(payload));

        log.info("Death case completed: case={}, journey={}", caseId, deathCase.getJourneyId());

        return deathCase;
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
            log.warn("Failed to serialise payload: {}", e.getMessage());
            return "{}";
        }
    }
}
