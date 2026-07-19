package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityAdminAppointmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityAdminAppointmentRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Expires facility-admin appointments whose validity window has passed
 * (V030, D-L4: every temporary management grant has an expiry that is
 * ENFORCED, not advisory). ACTIVE + valid_to < today → EXPIRED, with a
 * {@code tuso.facility.admin_appointment.expired} outbox event so org-registry
 * and the policy plane observe the loss of authority. Mirrors the varapi
 * {@code LicenceRenewalSweep} pattern.
 */
@Component
public class FacilityAdminAppointmentExpirySweep {

    public static final String EVENT_EXPIRED = "tuso.facility.admin_appointment.expired";

    private static final Logger log = LoggerFactory.getLogger(FacilityAdminAppointmentExpirySweep.class);

    private final FacilityAdminAppointmentRepository appointmentRepository;
    private final EventOutboxRepository outboxRepository;
    private final zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository facilityRepository;

    public FacilityAdminAppointmentExpirySweep(
            FacilityAdminAppointmentRepository appointmentRepository,
            EventOutboxRepository outboxRepository,
            zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository facilityRepository) {
        this.appointmentRepository = appointmentRepository;
        this.outboxRepository = outboxRepository;
        this.facilityRepository = facilityRepository;
    }

    @Scheduled(fixedDelayString = "${tuso.admin-appointment-sweep.interval-ms:3600000}")
    public void sweep() {
        int expired = expireLapsed(LocalDate.now());
        if (expired > 0) {
            log.info("Facility-admin appointment sweep: {} expired", expired);
        }
    }

    /** Testable unit: ACTIVE appointments with valid_to strictly before {@code asOf} → EXPIRED. */
    @Transactional
    public int expireLapsed(LocalDate asOf) {
        List<FacilityAdminAppointmentEntity> lapsed = appointmentRepository
                .findByApprovalStateAndValidToBefore(FacilityAdminAppointmentEntity.STATE_ACTIVE, asOf);
        for (FacilityAdminAppointmentEntity appointment : lapsed) {
            appointment.setApprovalState(FacilityAdminAppointmentEntity.STATE_EXPIRED);
            appointment.setUpdatedBy("SYSTEM_APPOINTMENT_SWEEP");
            appointmentRepository.save(appointment);
            publishExpired(appointment);
        }
        return lapsed.size();
    }

    private void publishExpired(FacilityAdminAppointmentEntity appointment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("facilityUuid", appointment.getFacilityUuid().toString());
        payload.put("appointmentId", appointment.getId());
        payload.put("personHealthId", appointment.getPersonHealthId());
        payload.put("role", appointment.getRole());
        payload.put("approvalState", appointment.getApprovalState());
        payload.put("validTo", String.valueOf(appointment.getValidTo()));

        String tenantId = facilityRepository.findByFacilityUuid(appointment.getFacilityUuid())
                .map(f -> f.getTenantId() != null ? f.getTenantId().toString() : null)
                .orElse(null);

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("FACILITY");
        outbox.setAggregateId(appointment.getFacilityUuid().toString());
        outbox.setTenantId(tenantId);
        outbox.setEventType(EVENT_EXPIRED);
        outbox.setPayload(payload);
        outbox.setProducer("tuso");
        outbox.setSubjectType("Facility");
        outbox.setSubjectId(appointment.getFacilityUuid().toString());
        outbox.setPartitionKey(appointment.getFacilityUuid().toString());
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(Instant.now().atOffset(ZoneOffset.UTC));
        outboxRepository.save(outbox);
    }
}
