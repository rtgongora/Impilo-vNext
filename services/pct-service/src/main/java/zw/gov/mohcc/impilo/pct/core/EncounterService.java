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
    private static final Set<String> ACTIVE_ENCOUNTER_STATES = Set.of("STARTED", "ON_HOLD");
    private static final Set<String> ALLOWED_MODALITIES = Set.of("in_person", "virtual", "hybrid");
    private static final Set<String> ALLOWED_VIRTUAL_MODES = Set.of("async", "chat", "audio", "video", "scheduled", "board");
    private static final Set<String> ALLOWED_CONTEXTS = Set.of(
            "outpatient", "emergency", "inpatient", "community", "virtual",
            "procedure", "procedure_room", "operating_room");
    private static final Set<String> ALLOWED_ENTRY_POINTS = Set.of(
            "scheduled_appointment", "walk_in", "emergency_arrival", "referral", "community_outreach",
            "virtual_request", "inpatient_admission", "transfer");
    private static final Set<String> ALLOWED_CARE_SETTINGS = Set.of("facility", "community", "home", "mobile_outreach", "virtual");
    private static final Set<String> ALLOWED_PRIORITIES = Set.of("routine", "urgent", "emergency");

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
    public EncounterEntity startEncounter(String journeyId,
                                          String encounterType,
                                          String encounterContext,
                                          String entryPoint,
                                          String modality,
                                          String virtualMode,
                                          String careSetting,
                                          String priority,
                                          String triageCategory,
                                          String pathwayRef,
                                          String protocolRef) {
        return startEncounter(journeyId, encounterType, encounterContext, entryPoint, modality,
                virtualMode, careSetting, priority, triageCategory, pathwayRef, protocolRef, null);
    }

    @Transactional
    public EncounterEntity startEncounter(String journeyId,
                                          String encounterType,
                                          String encounterContext,
                                          String entryPoint,
                                          String modality,
                                          String virtualMode,
                                          String careSetting,
                                          String priority,
                                          String triageCategory,
                                          String pathwayRef,
                                          String protocolRef,
                                          String shiftId) {
        TrustContext ctx = TrustContextHolder.require();

        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Journey not found: " + journeyId));

        ensureNoActiveEncounter(ctx.tenantId(), journeyId);

        EncounterEntity encounter = new EncounterEntity();
        encounter.setTenantId(ctx.tenantId());
        encounter.setEncounterRef(UUID.randomUUID());
        encounter.setJourneyId(journeyId);
        encounter.setSubjectCpid(journey.getPatientCpid());
        encounter.setFacilityId(ctx.facilityId());
        encounter.setWorkspaceId(ctx.workspaceId());
        encounter.setEncounterType(encounterType);
        encounter.setEncounterContext(normalizeEncounterContext(encounterContext, encounterType));
        encounter.setEntryPoint(normalizeEntryPoint(entryPoint, journey));
        String normalizedModality = normalizeModality(modality);
        String normalizedVirtualMode = normalizeVirtualMode(virtualMode, normalizedModality);
        encounter.setModality(normalizedModality);
        encounter.setVirtualMode(normalizedVirtualMode);
        encounter.setCareSetting(normalizeCareSetting(careSetting, normalizedModality));
        encounter.setPriority(normalizePriority(priority));
        encounter.setTriageCategory(normalizeOptionalUpperToken(triageCategory, "triage category"));
        encounter.setPathwayRef(normalizeOptionalReference(pathwayRef));
        encounter.setProtocolRef(normalizeOptionalReference(protocolRef));
        encounter.setStatus("STARTED");
        encounter.setAssignedProviderId(ctx.actorId());
        encounter.setShiftId(shiftId != null && !shiftId.isBlank() ? shiftId.trim() : null);
        encounter.setStartedAt(OffsetDateTime.now());

        encounter = encounterRepository.save(encounter);

        // Transition journey to IN_SERVICE if not already there
        if (journey.getState() != JourneyState.IN_SERVICE) {
            journeyStateMachine.transition(journey, JourneyState.IN_SERVICE);
        }

        // Outbox event (Kafka: pct.encounter.started — include tenant/event id for downstream idempotency)
        Map<String, Object> payload = new LinkedHashMap<>();
        String encounterRefStr = encounter.getEncounterRef().toString();
        payload.put("eventId", encounterRefStr + ":ENCOUNTER_STARTED");
        payload.put("tenantId", ctx.tenantId().toString());
        payload.put("encounterRef", encounterRefStr);
        payload.put("journeyId", journeyId);
        payload.put("patientCpid", journey.getPatientCpid());
        payload.put("encounterType", encounterType);
        payload.put("encounterContext", encounter.getEncounterContext());
        payload.put("entryPoint", encounter.getEntryPoint());
        payload.put("modality", encounter.getModality());
        payload.put("virtualMode", encounter.getVirtualMode());
        payload.put("careSetting", encounter.getCareSetting());
        payload.put("priority", encounter.getPriority());
        payload.put("triageCategory", encounter.getTriageCategory());
        payload.put("pathwayRef", encounter.getPathwayRef());
        payload.put("protocolRef", encounter.getProtocolRef());
        payload.put("assignedProvider", ctx.actorId());
        payload.put("shiftId", encounter.getShiftId());
        payload.put("workspaceId", ctx.workspaceId() != null ? ctx.workspaceId().toString() : null);
        payload.put("facilityId", ctx.facilityId().toString());
        payload.put("startedAt", encounter.getStartedAt().toString());
        writeOutbox("ENCOUNTER", encounter.getEncounterRef().toString(),
                "ENCOUNTER_STARTED", toJson(payload));

        log.info("Encounter started: ref={}, journey={}, type={}, provider={}",
                encounter.getEncounterRef(), journeyId, encounterType, ctx.actorId());

        return encounter;
    }

    private String normalizeModality(String modality) {
        String normalized = modality == null || modality.isBlank()
                ? "in_person"
                : modality.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MODALITIES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid encounter modality: " + modality);
        }
        return normalized;
    }

    private String normalizeVirtualMode(String virtualMode, String modality) {
        if ("in_person".equals(modality)) {
            return null;
        }
        if (virtualMode == null || virtualMode.isBlank()) {
            return "video";
        }
        String normalized = virtualMode.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_VIRTUAL_MODES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid virtual mode: " + virtualMode);
        }
        return normalized;
    }

    private String normalizeEncounterContext(String encounterContext, String encounterType) {
        String derivedDefault = deriveContextFromEncounterType(encounterType);
        String normalized = encounterContext == null || encounterContext.isBlank()
                ? derivedDefault
                : encounterContext.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTEXTS.contains(normalized)) {
            throw new IllegalArgumentException("Invalid encounter context: " + encounterContext);
        }
        return normalized;
    }

    private String normalizeEntryPoint(String entryPoint, JourneyEntity journey) {
        String normalized = entryPoint == null || entryPoint.isBlank()
                ? deriveEntryPointFromJourney(journey)
                : entryPoint.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_ENTRY_POINTS.contains(normalized)) {
            throw new IllegalArgumentException("Invalid encounter entry point: " + entryPoint);
        }
        return normalized;
    }

    private String normalizeCareSetting(String careSetting, String modality) {
        String defaultSetting = "virtual".equals(modality) ? "virtual" : "facility";
        String normalized = careSetting == null || careSetting.isBlank()
                ? defaultSetting
                : careSetting.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CARE_SETTINGS.contains(normalized)) {
            throw new IllegalArgumentException("Invalid encounter care setting: " + careSetting);
        }
        return normalized;
    }

    private String normalizePriority(String priority) {
        String normalized = priority == null || priority.isBlank()
                ? "routine"
                : priority.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_PRIORITIES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid encounter priority: " + priority);
        }
        return normalized;
    }

    private String normalizeOptionalUpperToken(String rawValue, String label) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 32) {
            throw new IllegalArgumentException("Encounter " + label + " is too long");
        }
        return normalized;
    }

    private String normalizeOptionalReference(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("Encounter pathway/protocol reference is too long");
        }
        return normalized;
    }

    private String deriveContextFromEncounterType(String encounterType) {
        if (encounterType == null || encounterType.isBlank()) {
            return "outpatient";
        }
        String upper = encounterType.trim().toUpperCase(Locale.ROOT);
        if (upper.contains("OPERATING_ROOM") || upper.contains("THEATRE") || upper.contains("OR_")) return "operating_room";
        if (upper.contains("PROCEDURE_ROOM")) return "procedure_room";
        if (upper.contains("PROCEDURE") || upper.contains("SURGERY")) return "procedure";
        if (upper.contains("EMERGENCY") || upper.contains("CASUALTY")) return "emergency";
        if (upper.contains("INPATIENT") || upper.contains("WARD")) return "inpatient";
        if (upper.contains("HOME") || upper.contains("COMMUNITY") || upper.contains("OUTREACH")) return "community";
        if (upper.contains("TELE") || upper.contains("VIRTUAL")) return "virtual";
        return "outpatient";
    }

    /**
     * Updates pathway/protocol linkage for an active or historical encounter.
     *
     * <p>Only linkage metadata is updated here; pathway execution remains owned by
     * guidance/rules/clinical-knowledge services.</p>
     */
    @Transactional
    public EncounterEntity updateEncounterPathwayProtocol(Long encounterId, String pathwayRef, String protocolRef) {
        EncounterEntity encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new IllegalArgumentException("Encounter not found: " + encounterId));

        encounter.setPathwayRef(normalizeOptionalReference(pathwayRef));
        encounter.setProtocolRef(normalizeOptionalReference(protocolRef));
        encounter = encounterRepository.save(encounter);

        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        String encounterRefStr = encounter.getEncounterRef().toString();
        payload.put("eventId", encounterRefStr + ":ENCOUNTER_PATHWAY_PROTOCOL_UPDATED");
        payload.put("tenantId", ctx.tenantId().toString());
        payload.put("encounterRef", encounterRefStr);
        payload.put("journeyId", encounter.getJourneyId());
        payload.put("pathwayRef", encounter.getPathwayRef());
        payload.put("protocolRef", encounter.getProtocolRef());
        payload.put("updatedAt", OffsetDateTime.now().toString());
        writeOutbox("ENCOUNTER", encounterRefStr,
                "ENCOUNTER_PATHWAY_PROTOCOL_UPDATED", toJson(payload));

        return encounter;
    }

    private String deriveEntryPointFromJourney(JourneyEntity journey) {
        String referralId = journey.getReferralId();
        String referralSource = journey.getReferralSource() == null ? "" : journey.getReferralSource().trim().toUpperCase(Locale.ROOT);
        if (referralId != null && !referralId.isBlank()) return "referral";
        if (referralSource.contains("EMERGENCY") || referralSource.contains("CASUALTY")) return "emergency_arrival";
        if (referralSource.contains("COMMUNITY") || referralSource.contains("OUTREACH")) return "community_outreach";
        if (referralSource.contains("TRANSFER")) return "transfer";
        if (referralSource.contains("APPOINTMENT") || referralSource.contains("BOOKING") || referralSource.contains("SCHEDULED")) {
            return "scheduled_appointment";
        }
        return "walk_in";
    }

    private void ensureNoActiveEncounter(UUID tenantId, String journeyId) {
        List<EncounterEntity> encounters = encounterRepository.findByTenantIdAndJourneyId(tenantId, journeyId);
        boolean hasActive = encounters.stream().anyMatch(encounter ->
                ACTIVE_ENCOUNTER_STATES.contains(encounter.getStatus() == null ? "" : encounter.getStatus().toUpperCase(Locale.ROOT)));
        if (hasActive) {
            throw new IllegalStateException("Encounter already active for journey: " + journeyId);
        }
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

        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        String encounterRefStr = encounter.getEncounterRef().toString();
        payload.put("eventId", encounterRefStr + ":ENCOUNTER_COMPLETED");
        payload.put("tenantId", ctx.tenantId().toString());
        payload.put("encounterRef", encounterRefStr);
        payload.put("journeyId", encounter.getJourneyId());
        payload.put("patientCpid", encounter.getSubjectCpid());
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
