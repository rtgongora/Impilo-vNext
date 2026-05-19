package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.api.dto.CreatePrescriptionRequest;
import zw.gov.mohcc.impilo.pharmacy.domain.PrescriptionStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PrescriptionEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.PrescriptionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PrescriptionServiceImpl(
            PrescriptionRepository prescriptionRepository,
            EventOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.prescriptionRepository = prescriptionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PrescriptionEntity createPrescription(CreatePrescriptionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        PrescriptionEntity entity = new PrescriptionEntity();
        entity.setTenantId(ctx.tenantId());
        entity.setWorkspaceId(ctx.workspaceId());
        entity.setPatientCpid(nonBlank(request.patient_id(), "patient_id"));
        entity.setEncounterId(blankToNull(request.encounter_id()));
        entity.setFacilityId(resolveFacilityId(request.facility_id(), ctx));
        entity.setMedicationName(nonBlank(request.medication_name(), "medication_name"));
        entity.setGenericName(blankToNull(request.generic_name()));
        entity.setDosage(nonBlank(request.dosage(), "dosage"));
        entity.setRoute(blankToNull(request.route()));
        entity.setFrequency(nonBlank(request.frequency(), "frequency"));
        entity.setDuration(blankToNull(request.duration()));
        entity.setQuantity(request.quantity() != null ? Math.max(request.quantity(), 1) : 1);
        entity.setInstructions(blankToNull(request.instructions()));
        entity.setIndication(blankToNull(request.indication()));
        entity.setPrescribedBy(nonBlank(request.prescribed_by(), "prescribed_by"));
        entity.setStatus(PrescriptionStatus.ACTIVE);
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        publishEvent("PRESCRIPTION", saved.getPrescriptionId().toString(), "PRESCRIPTION_CREATED", saved, ctx.tenantId());
        return saved;
    }

    @Override
    public Page<PrescriptionEntity> listPatientPrescriptions(String patientCpid, String status, Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        String normalizedPatient = nonBlank(patientCpid, "patient_cpid");
        PrescriptionStatus parsed = parseStatus(status);
        if (parsed == null) {
            return prescriptionRepository.findByTenantIdAndPatientCpidOrderByCreatedAtDesc(ctx.tenantId(), normalizedPatient, pageable);
        }
        return prescriptionRepository.findByTenantIdAndPatientCpidAndStatusOrderByCreatedAtDesc(
                ctx.tenantId(), normalizedPatient, parsed, pageable);
    }

    @Override
    public PrescriptionEntity getPrescription(UUID prescriptionId) {
        TrustContext ctx = TrustContextHolder.require();
        PrescriptionEntity entity = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found: " + prescriptionId));
        if (!ctx.tenantId().equals(entity.getTenantId())) {
            throw new SecurityException("Prescription tenant mismatch");
        }
        return entity;
    }

    @Override
    @Transactional
    public PrescriptionEntity cancelPrescription(UUID prescriptionId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        PrescriptionEntity entity = getPrescription(prescriptionId);
        if (entity.getStatus() == PrescriptionStatus.CANCELLED) {
            throw new IllegalStateException("Prescription already cancelled");
        }
        if (entity.getStatus() == PrescriptionStatus.DISPENSED) {
            throw new IllegalStateException("Cannot cancel dispensed prescription");
        }
        entity.setStatus(PrescriptionStatus.CANCELLED);
        entity.setCancelReason(blankToNull(reason));
        entity.setCancelledBy(ctx.actorId());
        entity.setCancelledAt(OffsetDateTime.now());
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        publishEvent("PRESCRIPTION", saved.getPrescriptionId().toString(), "PRESCRIPTION_CANCELLED", saved, ctx.tenantId());
        return saved;
    }

    @Override
    @Transactional
    public PrescriptionEntity requestRefill(UUID prescriptionId, Map<String, Object> payload) {
        TrustContext ctx = TrustContextHolder.require();
        PrescriptionEntity entity = getPrescription(prescriptionId);
        entity.setRefillRequestedAt(OffsetDateTime.now());
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        publishEvent("PRESCRIPTION", saved.getPrescriptionId().toString(), "PRESCRIPTION_REFILL_REQUESTED", Map.of(
                "prescription_id", saved.getPrescriptionId().toString(),
                "requested_at", saved.getRefillRequestedAt(),
                "payload", payload != null ? payload : Map.of()
        ), ctx.tenantId());
        return saved;
    }

    @Override
    @Transactional
    public PrescriptionEntity markDispensed(UUID prescriptionId, String dispensedBy) {
        TrustContext ctx = TrustContextHolder.require();
        PrescriptionEntity entity = getPrescription(prescriptionId);
        if (entity.getStatus() == PrescriptionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot dispense cancelled prescription");
        }
        entity.setStatus(PrescriptionStatus.DISPENSED);
        entity.setDispensedBy(nonBlank(dispensedBy, "dispensed_by"));
        entity.setDispensedAt(OffsetDateTime.now());
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        publishEvent("PRESCRIPTION", saved.getPrescriptionId().toString(), "PRESCRIPTION_DISPENSED", saved, ctx.tenantId());
        return saved;
    }

    private static String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static UUID resolveFacilityId(String facilityId, TrustContext ctx) {
        if (facilityId != null && !facilityId.isBlank()) {
            return UUID.fromString(facilityId.trim());
        }
        if (ctx.facilityId() != null) {
            return ctx.facilityId();
        }
        throw new IllegalArgumentException("facility_id is required");
    }

    private static PrescriptionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("PENDING".equals(normalized)) {
            return PrescriptionStatus.ACTIVE;
        }
        return PrescriptionStatus.valueOf(normalized);
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType, Object payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception ignored) {
            // Outbox failure must not hide the main mutation result.
        }
    }
}
