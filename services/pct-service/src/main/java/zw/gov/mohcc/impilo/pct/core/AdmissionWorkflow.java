package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.AdmissionRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Manages the inpatient admission workflow.
 *
 * <p>The admission workflow progresses through the following statuses:</p>
 * <ol>
 *   <li><strong>REQUESTED</strong> — a clinician has requested admission for a patient</li>
 *   <li><strong>APPROVED</strong> — a ward manager or authoriser has approved the request</li>
 *   <li><strong>ADMITTED</strong> — the patient has been physically admitted to a ward/bed</li>
 * </ol>
 *
 * <p>Admitting a patient transitions the journey to {@link JourneyState#ADMITTED}
 * and publishes outbox and telemetry events for downstream consumers (e.g.
 * bed management dashboards, billing, pharmacy).</p>
 *
 * <p>Bed assignment validation ensures that a bed is not double-occupied
 * by checking for existing admissions with active statuses (APPROVED or
 * ADMITTED) at the target ward/bed combination.</p>
 */
@Service
public class AdmissionWorkflow {

    private static final Logger log = LoggerFactory.getLogger(AdmissionWorkflow.class);

    /** Admission statuses that indicate a bed is occupied. */
    private static final List<String> ACTIVE_STATUSES = List.of("APPROVED", "ADMITTED");

    private final AdmissionRepository admissionRepository;
    private final JourneyRepository journeyRepository;
    private final JourneyStateMachine journeyStateMachine;
    private final EventOutboxRepository outboxRepository;
    private final TelemetryService telemetryService;
    private final ObjectMapper objectMapper;
    private final EncounterRepository encounterRepository;

    public AdmissionWorkflow(AdmissionRepository admissionRepository,
                             JourneyRepository journeyRepository,
                             JourneyStateMachine journeyStateMachine,
                             EventOutboxRepository outboxRepository,
                             TelemetryService telemetryService,
                             ObjectMapper objectMapper,
                             EncounterRepository encounterRepository) {
        this.admissionRepository = admissionRepository;
        this.journeyRepository = journeyRepository;
        this.journeyStateMachine = journeyStateMachine;
        this.outboxRepository = outboxRepository;
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
        this.encounterRepository = encounterRepository;
    }

    /**
     * Resolves the encounter this admission belongs to, as a real {@code pct_encounters.encounter_ref}.
     *
     * <p>{@code admissions.encounter_id} is handed to inpatient-service on approval and becomes the
     * census parent key. inpatient then writes it into the clinical record as
     * {@code "Encounter/" + encounterId} — on discharge summaries, operative notes, specimens and
     * pathology references. Until now it was whatever the API caller supplied, stored unvalidated,
     * and nothing in PCT ever copied the real {@code encounter_ref} into it. So the value inpatient
     * carried was a UUID of unverified provenance that might match no encounter at all, and every
     * FHIR reference built from it dangled.</p>
     *
     * <p>Deriving from the journey — the authoritative source — makes it genuinely an
     * {@code encounter_ref}, which is what lets a consumer resolve
     * {@code Encounter?identifier=https://impilo.gov.zw/pct/encounter-id|{ref}} against the SHR.</p>
     *
     * <p>A supplied value that belongs to a different journey is <b>not</b> a reason to refuse the
     * admission. Blocking a patient from being admitted over a bad reference trades a record
     * linkage gap for clinical harm, which is the wrong way round. It is logged and replaced with
     * the journey's own encounter instead — and if the journey has none, the admission proceeds
     * with no reference rather than a fabricated one.</p>
     */
    private UUID resolveEncounterRef(String journeyId, UUID tenantId, UUID supplied) {
        List<EncounterEntity> encounters =
                encounterRepository.findByTenantIdAndJourneyId(tenantId, journeyId);

        if (supplied != null) {
            boolean belongsToThisJourney = encounters.stream()
                    .anyMatch(e -> supplied.equals(e.getEncounterRef()));
            if (belongsToThisJourney) {
                return supplied;
            }
            log.warn("PCT admission: supplied encounterId {} is not an encounter_ref on journey {} — "
                            + "deriving from the journey instead, so the reference inpatient writes "
                            + "into the clinical record resolves", supplied, journeyId);
        }

        // The visit still open is the one an admission belongs to; otherwise the most recent.
        return encounters.stream()
                .filter(e -> !"COMPLETED".equalsIgnoreCase(e.getStatus()))
                .findFirst()
                .or(() -> encounters.stream().reduce((first, second) -> second))
                .map(EncounterEntity::getEncounterRef)
                .orElseGet(() -> {
                    log.info("PCT admission: journey {} has no encounter — admission recorded with "
                            + "no encounter reference rather than a fabricated one", journeyId);
                    return null;
                });
    }

    /**
     * Request admission for a patient to a specific ward and bed.
     *
     * <p>Creates an admission entity with status {@code REQUESTED}. The bed
     * is not yet reserved at this stage; reservation occurs at approval or
     * admission. The requesting clinician is recorded from the trust context.</p>
     *
     * @param journeyId the patient journey requiring admission
     * @param wardId    the target ward
     * @param bedId     the target bed within the ward; may be {@code null} if bed assignment is deferred
     * @return the created admission entity
     * @throws IllegalArgumentException if the journey is not found
     */
    @Transactional
    public AdmissionEntity requestAdmission(String journeyId, UUID wardId, UUID bedId) {
        return requestAdmission(journeyId, wardId, bedId, null, null, null);
    }

    /**
     * Request admission, capturing the clinical context handed to inpatient-service on approval.
     *
     * @param encounterId        a preferred {@code pct_encounters.encounter_ref}; may be {@code null}.
     *                           Honoured only if it belongs to this journey — otherwise the journey's
     *                           own encounter is used, so the reference inpatient writes into the
     *                           clinical record resolves. See {@link #resolveEncounterRef}.
     * @param admittingDiagnosis clinical context; may be {@code null}
     * @param admissionType      ELECTIVE | EMERGENCY | TRANSFER; may be {@code null}
     */
    @Transactional
    public AdmissionEntity requestAdmission(String journeyId, UUID wardId, UUID bedId,
                                            UUID encounterId, String admittingDiagnosis, String admissionType) {
        TrustContext ctx = TrustContextHolder.require();

        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Journey not found: " + journeyId));

        AdmissionEntity admission = new AdmissionEntity();
        admission.setId(UUID.randomUUID());
        admission.setJourneyId(journeyId);
        admission.setTenantId(ctx.tenantId());
        admission.setFacilityId(journey.getFacilityId());
        admission.setPatientCpid(journey.getPatientCpid());
        admission.setWardId(wardId);
        admission.setBedId(bedId);
        admission.setEncounterId(resolveEncounterRef(journeyId, ctx.tenantId(), encounterId));
        admission.setAdmittingDiagnosis(admittingDiagnosis);
        admission.setAdmissionType(admissionType);
        admission.setStatus("REQUESTED");
        admission.setRequestedBy(ctx.actorId());
        admission.setCreatedAt(OffsetDateTime.now());
        admission.setUpdatedAt(OffsetDateTime.now());

        admission = admissionRepository.save(admission);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("admissionId", admission.getId().toString());
        payload.put("journeyId", journeyId);
        payload.put("patientCpid", journey.getPatientCpid());
        payload.put("wardId", wardId != null ? wardId.toString() : null);
        payload.put("bedId", bedId != null ? bedId.toString() : null);
        payload.put("requestedBy", ctx.actorId());
        writeOutbox("ADMISSION", admission.getId().toString(),
                "ADMISSION_REQUESTED", toJson(payload));

        log.info("Admission requested: id={}, journey={}, ward={}, bed={}",
                admission.getId(), journeyId, wardId, bedId);

        return admission;
    }

    /**
     * Approve a pending admission request.
     *
     * <p>Transitions the admission status from {@code REQUESTED} to
     * {@code APPROVED}. The approving actor is recorded from the trust
     * context.</p>
     *
     * @param admissionId the admission to approve
     * @return the updated admission entity
     * @throws IllegalArgumentException if the admission is not found
     * @throws IllegalStateException    if the admission is not in REQUESTED status
     */
    @Transactional
    public AdmissionEntity approveAdmission(UUID admissionId) {
        TrustContext ctx = TrustContextHolder.require();

        AdmissionEntity admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));

        if (!"REQUESTED".equals(admission.getStatus())) {
            throw new IllegalStateException(
                    "Cannot approve admission in status: " + admission.getStatus());
        }

        admission.setStatus("APPROVED");
        admission.setApprovedBy(ctx.actorId());
        admission.setUpdatedAt(OffsetDateTime.now());

        admission = admissionRepository.save(admission);

        // PCT<->inpatient handshake: PCT owns the admission DECISION; inpatient-service owns the physical
        // census (bed/ward, bed-day accrual). On approval, hand the clinical context to inpatient so it can
        // create the census admission and assign a bed. inpatient echoes back with the admission_ref + bed,
        // which PCT stamps via linkInpatientAdmission(). Field ownership is documented in V018.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("admissionId", admission.getId().toString());
        payload.put("journeyId", admission.getJourneyId());
        payload.put("patientCpid", admission.getPatientCpid());
        payload.put("subjectCpid", admission.getPatientCpid());
        payload.put("facilityId", admission.getFacilityId() != null ? admission.getFacilityId().toString() : null);
        payload.put("encounterId", admission.getEncounterId() != null ? admission.getEncounterId().toString() : null);
        payload.put("wardId", admission.getWardId() != null ? admission.getWardId().toString() : null);
        payload.put("bedId", admission.getBedId() != null ? admission.getBedId().toString() : null);
        payload.put("admittingDiagnosis", admission.getAdmittingDiagnosis());
        payload.put("admissionType", admission.getAdmissionType());
        payload.put("approvedBy", ctx.actorId());
        writeOutbox("ADMISSION", admission.getId().toString(), "ADMISSION_APPROVED", toJson(payload));

        log.info("Admission approved: id={}, journey={} — handed to inpatient for bed assignment",
                admissionId, admission.getJourneyId());

        return admission;
    }

    /**
     * Stamp the inpatient-service admission reference and assigned bed back onto the PCT admission.
     *
     * <p>Called when inpatient-service echoes its bed-assignment event after creating the census admission
     * (the second half of the PCT&lt;-&gt;inpatient handshake). PCT remains the SoR for the admission decision;
     * inpatient remains the SoR for the bed/ward census. This records the cross-service link only.</p>
     *
     * @param admissionId        the PCT admission
     * @param inpatientRef       inpatient-service admission_ref
     * @param wardId             ward inpatient assigned (may be {@code null})
     * @param bedId              bed inpatient assigned (may be {@code null})
     * @return the updated PCT admission, or {@code null} if no matching admission exists
     */
    @Transactional
    public AdmissionEntity linkInpatientAdmission(UUID admissionId, UUID inpatientRef, UUID wardId, UUID bedId) {
        Optional<AdmissionEntity> found = admissionRepository.findById(admissionId);
        if (found.isEmpty()) {
            log.warn("PCT admission {} not found for inpatient link (inpatientRef={})", admissionId, inpatientRef);
            return null;
        }
        AdmissionEntity admission = found.get();
        admission.setInpatientAdmissionRef(inpatientRef);
        if (wardId != null) {
            admission.setWardId(wardId);
        }
        if (bedId != null) {
            admission.setBedId(bedId);
        }
        admission.setBedAssignedAt(OffsetDateTime.now());
        admission.setUpdatedAt(OffsetDateTime.now());
        admission = admissionRepository.save(admission);
        log.info("PCT admission {} linked to inpatient admission_ref {} (ward={}, bed={})",
                admissionId, inpatientRef, wardId, bedId);
        return admission;
    }

    /**
     * Admit the patient (physically place them in the ward/bed).
     *
     * <p>Transitions the admission to {@code ADMITTED} status, records the
     * admitting provider and timestamp, and transitions the journey to
     * {@link JourneyState#ADMITTED}. Publishes outbox and telemetry events
     * for bed management and billing systems.</p>
     *
     * @param admissionId the admission to complete
     * @return the updated admission entity
     * @throws IllegalArgumentException if the admission is not found
     * @throws IllegalStateException    if the admission is not in REQUESTED or APPROVED status
     */
    @Transactional
    public AdmissionEntity admitPatient(UUID admissionId) {
        TrustContext ctx = TrustContextHolder.require();

        AdmissionEntity admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));

        if (!"REQUESTED".equals(admission.getStatus()) && !"APPROVED".equals(admission.getStatus())) {
            throw new IllegalStateException(
                    "Cannot admit patient from admission status: " + admission.getStatus());
        }

        admission.setStatus("ADMITTED");
        admission.setAdmittedBy(ctx.actorId());
        admission.setAdmittedAt(OffsetDateTime.now());
        admission.setUpdatedAt(OffsetDateTime.now());

        String journeyId = admission.getJourneyId();
        admission = admissionRepository.save(admission);

        // Transition journey to ADMITTED
        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new IllegalStateException(
                        "Journey not found for admission: " + journeyId));
        journeyStateMachine.transition(journey, JourneyState.ADMITTED);

        // Outbox event
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("admissionId", admission.getId().toString());
        payload.put("journeyId", journeyId);
        payload.put("patientCpid", admission.getPatientCpid());
        payload.put("wardId", admission.getWardId().toString());
        payload.put("bedId", admission.getBedId() != null ? admission.getBedId().toString() : null);
        payload.put("admittedBy", ctx.actorId());
        payload.put("admittedAt", admission.getAdmittedAt().toString());
        writeOutbox("ADMISSION", admission.getId().toString(),
                "PATIENT_ADMITTED", toJson(payload));

        // Telemetry
        Map<String, Object> telemetryData = new LinkedHashMap<>();
        telemetryData.put("admissionId", admission.getId().toString());
        telemetryData.put("wardId", admission.getWardId().toString());
        telemetryService.record("admission.admitted", journeyId, telemetryData);

        log.info("Patient admitted: admission={}, journey={}, ward={}, bed={}",
                admissionId, journeyId, admission.getWardId(), admission.getBedId());

        return admission;
    }

    /**
     * Assign or reassign a bed for an existing admission.
     *
     * <p>Validates that the target bed is not already occupied by another
     * patient with an active admission (APPROVED or ADMITTED status). If
     * the bed is occupied, an {@link IllegalStateException} is thrown.</p>
     *
     * @param admissionId the admission to update
     * @param wardId      the target ward
     * @param bedId       the target bed within the ward
     * @return the updated admission entity
     * @throws IllegalArgumentException if the admission is not found
     * @throws IllegalStateException    if the target bed is already occupied
     */
    @Transactional
    public AdmissionEntity assignBed(UUID admissionId, UUID wardId, UUID bedId) {
        AdmissionEntity admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));

        // Validate bed is not occupied by another patient
        boolean bedOccupied = admissionRepository
                .existsByWardIdAndBedIdAndStatusInAndIdNot(wardId, bedId, ACTIVE_STATUSES, admissionId);

        if (bedOccupied) {
            throw new IllegalStateException(String.format(
                    "Bed %s in ward %s is already occupied by another patient", bedId, wardId));
        }

        admission.setWardId(wardId);
        admission.setBedId(bedId);
        admission.setUpdatedAt(OffsetDateTime.now());

        admission = admissionRepository.save(admission);

        log.info("Bed assigned: admission={}, ward={}, bed={}", admissionId, wardId, bedId);

        return admission;
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
