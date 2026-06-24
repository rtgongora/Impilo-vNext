package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inpatient.api.dto.CreateAdmissionRequest;
import zw.gov.mohcc.impilo.inpatient.api.dto.PatientLocationView;
import zw.gov.mohcc.impilo.inpatient.api.dto.TransferRequest;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.BedEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.TransferEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.AdmissionRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.BedRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.TransferRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.WardRepository;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Core service for inpatient admission, transfer, and discharge workflows.
 * Each state transition appends an event to the outbox for reliable Kafka publishing.
 */
@Service
public class AdmissionService {

    private static final Logger log = LoggerFactory.getLogger(AdmissionService.class);

    private final AdmissionRepository admissionRepository;
    private final TransferRepository transferRepository;
    private final EventOutboxRepository outboxRepository;
    private final WardRepository wardRepository;
    private final BedRepository bedRepository;
    private final ObjectMapper objectMapper;

    public AdmissionService(AdmissionRepository admissionRepository,
                            TransferRepository transferRepository,
                            EventOutboxRepository outboxRepository,
                            WardRepository wardRepository,
                            BedRepository bedRepository,
                            ObjectMapper objectMapper) {
        this.admissionRepository = admissionRepository;
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
        this.wardRepository = wardRepository;
        this.bedRepository = bedRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists admissions, optionally filtered by facility and/or status.
     */
    public List<AdmissionEntity> listAdmissions(UUID facilityId, String status, String subjectCpid) {
        if (subjectCpid != null && !subjectCpid.isBlank()) {
            if (status != null) {
                return admissionRepository.findBySubjectCpidAndStatus(subjectCpid, status);
            }
            return admissionRepository.findBySubjectCpid(subjectCpid);
        }
        if (facilityId != null && status != null) {
            return admissionRepository.findByFacilityIdAndStatus(facilityId, status);
        } else if (facilityId != null) {
            return admissionRepository.findByFacilityId(facilityId);
        } else if (status != null) {
            return admissionRepository.findByStatus(status);
        }
        return admissionRepository.findAll();
    }

    /**
     * Retrieves a single admission by its unique reference.
     */
    public AdmissionEntity getAdmission(UUID admissionRef) {
        return admissionRepository.findByAdmissionRef(admissionRef)
                .orElseThrow(() -> new AdmissionNotFoundException(
                        "Admission not found: " + admissionRef));
    }

    /**
     * Active admissions for a patient at a facility (typically at most one ADMITTED row).
     */
    public List<AdmissionEntity> findActiveAdmissionsForPatientAtFacility(String subjectCpid, UUID facilityId) {
        return admissionRepository.findBySubjectCpidAndFacilityIdAndStatus(subjectCpid, facilityId, "ADMITTED");
    }

    /**
     * Resolves a patient's current inpatient location — the most-recent ADMITTED admission
     * (optionally scoped to a facility) with ward/bed UUIDs resolved to human-readable labels.
     * Returns empty when the patient has no active admission (i.e. they are not an inpatient).
     * Backs the experience-shell patient-location badge (G053).
     */
    public Optional<PatientLocationView> getCurrentLocation(String subjectCpid, UUID facilityId) {
        if (subjectCpid == null || subjectCpid.isBlank()) {
            return Optional.empty();
        }
        List<AdmissionEntity> active = facilityId != null
                ? admissionRepository.findBySubjectCpidAndFacilityIdAndStatus(subjectCpid, facilityId, "ADMITTED")
                : admissionRepository.findBySubjectCpidAndStatus(subjectCpid, "ADMITTED");
        return active.stream()
                .max(Comparator.comparing(AdmissionEntity::getAdmittedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(this::toLocationView);
    }

    private PatientLocationView toLocationView(AdmissionEntity admission) {
        String wardName = admission.getWardId() == null ? null
                : wardRepository.findById(admission.getWardId()).map(WardEntity::getName).orElse(null);
        String bedNumber = admission.getBedId() == null ? null
                : bedRepository.findById(admission.getBedId()).map(BedEntity::getBedNumber).orElse(null);
        return new PatientLocationView(
                admission.getAdmissionRef(),
                admission.getStatus(),
                admission.getFacilityId(),
                admission.getWardId(),
                wardName,
                admission.getBedId(),
                bedNumber,
                admission.getAdmissionType(),
                admission.getAdmittedAt());
    }

    /**
     * Creates a new admission and writes an outbox event for Kafka publication.
     */
    @Transactional
    public AdmissionEntity createAdmission(CreateAdmissionRequest request) {
        AdmissionEntity admission = new AdmissionEntity();
        admission.setAdmissionRef(UUID.randomUUID());
        admission.setTenantId(request.getTenantId());
        admission.setEncounterId(request.getEncounterId());
        admission.setSubjectCpid(request.getSubjectCpid());
        admission.setFacilityId(request.getFacilityId());
        admission.setWardId(request.getWardId());
        admission.setBedId(request.getBedId());
        admission.setAdmittingDiagnosis(request.getAdmittingDiagnosis());
        admission.setAdmissionType(request.getAdmissionType() != null ? request.getAdmissionType() : "EMERGENCY");
        admission.setDietOrders(request.getDietOrders() != null ? request.getDietOrders() : "REGULAR");
        admission.setActivityLevel(request.getActivityLevel() != null ? request.getActivityLevel() : "BED_REST");
        admission.setStatus("ADMITTED");
        admission.setAdmittedAt(OffsetDateTime.now());
        admission.setCreatedAt(OffsetDateTime.now());

        admission = admissionRepository.save(admission);

        appendOutboxEvent(
                "ADMISSION",
                admission.getAdmissionRef().toString(),
                "inpatient.admission.created",
                admission.getTenantId(),
                buildStandardPayload(admission));

        log.info("Admission created: admissionRef={}, facility={}, subject={}",
                admission.getAdmissionRef(), admission.getFacilityId(), admission.getSubjectCpid());

        return admission;
    }

    /**
     * Transfers a patient to a new ward/bed. Records the transfer and
     * updates the admission entity with the new ward and bed.
     */
    @Transactional
    public TransferEntity transferPatient(UUID admissionRef, TransferRequest request) {
        AdmissionEntity admission = getAdmission(admissionRef);

        if ("DISCHARGED".equals(admission.getStatus())) {
            throw new IllegalStateException("Cannot transfer a discharged patient: " + admissionRef);
        }

        TransferEntity transfer = new TransferEntity();
        transfer.setTenantId(admission.getTenantId());
        transfer.setAdmissionRef(admissionRef);
        transfer.setFromWardId(admission.getWardId());
        transfer.setToWardId(request.getToWardId());
        transfer.setToBedId(request.getToBedId());
        transfer.setTransferredAt(OffsetDateTime.now());
        transfer.setCreatedAt(OffsetDateTime.now());

        transfer = transferRepository.save(transfer);

        admission.setWardId(request.getToWardId());
        admission.setBedId(request.getToBedId());
        admission.setStatus("ADMITTED");
        admissionRepository.save(admission);

        Map<String, Object> payload = buildStandardPayload(admission);
        payload.put("admission_ref", admissionRef.toString());
        payload.put("from_ward_id", transfer.getFromWardId() != null ? transfer.getFromWardId().toString() : null);
        payload.put("to_ward_id", transfer.getToWardId().toString());
        payload.put("to_bed_id", transfer.getToBedId() != null ? transfer.getToBedId().toString() : null);
        payload.put("transferred_at", transfer.getTransferredAt().toString());

        appendOutboxEvent(
                "TRANSFER",
                String.valueOf(transfer.getId()),
                "inpatient.transfer.completed",
                admission.getTenantId(),
                payload);

        log.info("Patient transferred: admissionRef={}, fromWard={}, toWard={}",
                admissionRef, transfer.getFromWardId(), transfer.getToWardId());

        return transfer;
    }

    /**
     * Discharges a patient, setting the status to DISCHARGED and recording the timestamp.
     */
    @Transactional
    public AdmissionEntity dischargePatient(UUID admissionRef) {
        AdmissionEntity admission = getAdmission(admissionRef);

        if ("DISCHARGED".equals(admission.getStatus())) {
            throw new IllegalStateException("Patient already discharged: " + admissionRef);
        }

        admission.setStatus("DISCHARGED");
        admission.setDischargedAt(OffsetDateTime.now());
        admission = admissionRepository.save(admission);

        appendOutboxEvent(
                "ADMISSION",
                admissionRef.toString(),
                "inpatient.discharge.completed",
                admission.getTenantId(),
                buildStandardPayload(admission));

        log.info("Patient discharged: admissionRef={}, facility={}",
                admissionRef, admission.getFacilityId());

        return admission;
    }

    private Map<String, Object> buildStandardPayload(AdmissionEntity admission) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("patient_cpid", admission.getSubjectCpid());
        payload.put("facility_id", admission.getFacilityId().toString());
        payload.put("ward_id", admission.getWardId() != null ? admission.getWardId().toString() : null);
        payload.put("bed_id", admission.getBedId() != null ? admission.getBedId().toString() : null);
        payload.put("admission_ref", admission.getAdmissionRef().toString());
        payload.put("tenant_id", admission.getTenantId().toString());
        payload.put("encounter_id", admission.getEncounterId().toString());
        payload.put("status", admission.getStatus());
        payload.put("admitted_at", admission.getAdmittedAt() != null ? admission.getAdmittedAt().toString() : null);
        payload.put("discharged_at", admission.getDischargedAt() != null ? admission.getDischargedAt().toString() : null);
        return payload;
    }

    private void appendOutboxEvent(String aggregateType, String aggregateId, String eventType,
                                   UUID tenantId, Map<String, Object> payload) {
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
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
        outboxRepository.save(outbox);
    }
}
