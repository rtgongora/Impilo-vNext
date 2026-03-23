package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.AdmissionRepository;
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

    public AdmissionWorkflow(AdmissionRepository admissionRepository,
                             JourneyRepository journeyRepository,
                             JourneyStateMachine journeyStateMachine,
                             EventOutboxRepository outboxRepository,
                             TelemetryService telemetryService,
                             ObjectMapper objectMapper) {
        this.admissionRepository = admissionRepository;
        this.journeyRepository = journeyRepository;
        this.journeyStateMachine = journeyStateMachine;
        this.outboxRepository = outboxRepository;
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
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
        admission.setStatus("REQUESTED");
        admission.setRequestedBy(ctx.actorId());
        admission.setCreatedAt(OffsetDateTime.now());
        admission.setUpdatedAt(OffsetDateTime.now());

        admission = admissionRepository.save(admission);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("admissionId", admission.getId().toString());
        payload.put("journeyId", journeyId);
        payload.put("patientCpid", journey.getPatientCpid());
        payload.put("wardId", wardId.toString());
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

        log.info("Admission approved: id={}, journey={}", admissionId, admission.getJourneyId());

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

        admission = admissionRepository.save(admission);

        // Transition journey to ADMITTED
        final AdmissionEntity savedAdmission = admission;
        JourneyEntity journey = journeyRepository.findByJourneyId(savedAdmission.getJourneyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Journey not found for admission: " + savedAdmission.getJourneyId()));
        journeyStateMachine.transition(journey, JourneyState.ADMITTED);

        // Outbox event
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("admissionId", admission.getId().toString());
        payload.put("journeyId", admission.getJourneyId());
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
        telemetryService.record("admission.admitted", admission.getJourneyId(), telemetryData);

        log.info("Patient admitted: admission={}, journey={}, ward={}, bed={}",
                admissionId, admission.getJourneyId(), admission.getWardId(), admission.getBedId());

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
