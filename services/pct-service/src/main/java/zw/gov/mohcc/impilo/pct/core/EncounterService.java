package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Manages clinical encounter lifecycle within a patient journey.
 *
 * <p>An encounter represents a period of clinical interaction between a
 * provider and a patient. It is linked to a journey by {@code journeyId}
 * and carries the patient's CPID, the workspace (service point), and the
 * assigned provider. Multiple encounters may exist per journey (e.g. triage
 * encounter, consultation encounter, procedure encounter).</p>
 *
 * <p>Starting an encounter transitions the journey to {@link JourneyState#IN_SERVICE}
 * and publishes an outbox event. Completing an encounter records the end
 * timestamp and publishes a completion event. An optional BUTANO encounter
 * reference can be linked after the FHIR Encounter resource is created in
 * the Shared Health Record.</p>
 */
@Service
public class EncounterService {

    private static final Logger log = LoggerFactory.getLogger(EncounterService.class);

    private final EncounterRepository encounterRepository;
    private final JourneyRepository journeyRepository;
    private final JourneyStateMachine journeyStateMachine;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public EncounterService(EncounterRepository encounterRepository,
                            JourneyRepository journeyRepository,
                            JourneyStateMachine journeyStateMachine,
                            EventOutboxRepository outboxRepository,
                            ObjectMapper objectMapper) {
        this.encounterRepository = encounterRepository;
        this.journeyRepository = journeyRepository;
        this.journeyStateMachine = journeyStateMachine;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Start a new clinical encounter for a patient journey.
     *
     * <p>Creates an encounter entity with status {@code STARTED}, links it to
     * the journey, and assigns the current actor as the provider. The workspace
     * is taken from the trust context. The journey is transitioned to
     * {@link JourneyState#IN_SERVICE} if it is not already in that state.</p>
     *
     * @param journeyId     the patient journey this encounter belongs to
     * @param encounterType the type of encounter (e.g. CONSULTATION, PROCEDURE, TRIAGE, LAB)
     * @return the newly created encounter entity
     * @throws IllegalArgumentException if the journey is not found
     */
    @Transactional
    public EncounterEntity startEncounter(String journeyId, String encounterType) {
        TrustContext ctx = TrustContextHolder.require();

        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Journey not found: " + journeyId));

        EncounterEntity encounter = new EncounterEntity();
        encounter.setTenantId(ctx.tenantId());
        encounter.setEncounterRef(UUID.randomUUID());
        encounter.setJourneyId(journeyId);
        encounter.setSubjectCpid(journey.getPatientCpid());
        encounter.setFacilityId(ctx.facilityId());
        encounter.setWorkspaceId(ctx.workspaceId());
        encounter.setEncounterType(encounterType);
        encounter.setStatus("STARTED");
        encounter.setAssignedProvider(ctx.actorId());
        encounter.setStartedAt(OffsetDateTime.now());

        encounter = encounterRepository.save(encounter);

        // Transition journey to IN_SERVICE if not already there
        if (journey.getState() != JourneyState.IN_SERVICE) {
            journeyStateMachine.transition(journey, JourneyState.IN_SERVICE);
        }

        // Outbox event
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("encounterRef", encounter.getEncounterRef().toString());
        payload.put("journeyId", journeyId);
        payload.put("patientCpid", journey.getPatientCpid());
        payload.put("encounterType", encounterType);
        payload.put("assignedProvider", ctx.actorId());
        payload.put("workspaceId", ctx.workspaceId() != null ? ctx.workspaceId().toString() : null);
        payload.put("facilityId", ctx.facilityId().toString());
        payload.put("startedAt", encounter.getStartedAt().toString());
        writeOutbox("ENCOUNTER", encounter.getEncounterRef().toString(),
                "ENCOUNTER_STARTED", toJson(payload));

        log.info("Encounter started: ref={}, journey={}, type={}, provider={}",
                encounter.getEncounterRef(), journeyId, encounterType, ctx.actorId());

        return encounter;
    }

    /**
     * Complete a clinical encounter.
     *
     * <p>Sets the encounter status to {@code COMPLETED} and records the
     * end timestamp. Publishes an outbox event for downstream consumers
     * (e.g. billing, analytics).</p>
     *
     * @param encounterId the encounter primary key
     * @return the completed encounter entity
     * @throws IllegalArgumentException if the encounter is not found
     */
    @Transactional
    public EncounterEntity completeEncounter(Long encounterId) {
        EncounterEntity encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new IllegalArgumentException("Encounter not found: " + encounterId));

        encounter.setStatus("COMPLETED");
        encounter.setEndedAt(OffsetDateTime.now());
        encounter = encounterRepository.save(encounter);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("encounterRef", encounter.getEncounterRef().toString());
        payload.put("journeyId", encounter.getJourneyId());
        payload.put("encounterType", encounter.getEncounterType());
        payload.put("endedAt", encounter.getEndedAt().toString());
        writeOutbox("ENCOUNTER", encounter.getEncounterRef().toString(),
                "ENCOUNTER_COMPLETED", toJson(payload));

        log.info("Encounter completed: ref={}, journey={}",
                encounter.getEncounterRef(), encounter.getJourneyId());

        return encounter;
    }

    /**
     * Place an encounter on hold (e.g. awaiting lab results or imaging).
     *
     * @param encounterId the encounter primary key
     * @return the updated encounter entity
     * @throws IllegalArgumentException if the encounter is not found
     */
    @Transactional
    public EncounterEntity holdEncounter(Long encounterId) {
        EncounterEntity encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new IllegalArgumentException("Encounter not found: " + encounterId));

        encounter.setStatus("ON_HOLD");
        encounter = encounterRepository.save(encounter);

        log.info("Encounter placed on hold: ref={}", encounter.getEncounterRef());

        return encounter;
    }

    /**
     * Retrieve all encounters for a given journey.
     *
     * @param journeyId the patient journey identifier
     * @return a list of encounter entities (may be empty)
     */
    @Transactional(readOnly = true)
    public List<EncounterEntity> getEncountersByJourney(String journeyId) {
        return encounterRepository.findByJourneyId(journeyId);
    }

    /**
     * Link a BUTANO (Shared Health Record) FHIR Encounter reference to a
     * local encounter.
     *
     * <p>Called after the FHIR Encounter resource has been created in
     * BUTANO to establish bidirectional traceability between the local
     * PCT encounter and the FHIR SHR resource.</p>
     *
     * @param encounterId the local encounter primary key
     * @param butanoRef   the FHIR Encounter resource reference (e.g. "Encounter/abc-123")
     * @return the updated encounter entity
     * @throws IllegalArgumentException if the encounter is not found
     */
    @Transactional
    public EncounterEntity linkButanoRef(Long encounterId, String butanoRef) {
        EncounterEntity encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new IllegalArgumentException("Encounter not found: " + encounterId));

        encounter.setButanoEncounterRef(butanoRef);
        encounter = encounterRepository.save(encounter);

        log.info("BUTANO reference linked: encounter={}, butanoRef={}",
                encounter.getEncounterRef(), butanoRef);

        return encounter;
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
