package zw.gov.mohcc.impilo.ubomi.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.DeathNotificationEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.DeathNotificationRepository;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for death notifications.
 *
 * Lifecycle: SUBMITTED -> CERTIFIED -> REGISTERED (or REJECTED / CANCELLED)
 *
 * On CERTIFIED, a medical practitioner has certified the cause of death.
 * On REGISTERED, publishes a DEATH_REGISTERED event so that:
 *   - VITO marks the CPID as DECEASED
 *   - BUTANO closes open SHR encounters
 *   - Civil registry issues the death certificate
 */
@Service
public class DeathNotificationService {

    private final DeathNotificationRepository deathRepository;
    private final EventOutboxRepository outboxRepository;

    public DeathNotificationService(DeathNotificationRepository deathRepository,
                                     EventOutboxRepository outboxRepository) {
        this.deathRepository = deathRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Paginated listing of death notifications for a tenant.
     */
    @Transactional(readOnly = true)
    public Page<DeathNotificationEntity> list(UUID tenantId, int page, int size) {
        return deathRepository.findByTenantId(tenantId, PageRequest.of(page, size));
    }

    /**
     * Submit a new death notification.
     * Sets initial status to SUBMITTED and publishes a DEATH_SUBMITTED event.
     */
    @Transactional
    public DeathNotificationEntity submit(DeathNotificationEntity entity) {
        entity.setStatus("SUBMITTED");
        entity = deathRepository.save(entity);

        publishEvent("DEATH_NOTIFICATION", entity.getId().toString(),
                "DEATH_SUBMITTED",
                String.format("{\"notificationId\":%d,\"notificationNumber\":\"%s\",\"tenantId\":\"%s\",\"deceasedCpid\":\"%s\"}",
                        entity.getId(), entity.getNotificationNumber(),
                        entity.getTenantId(), entity.getDeceasedCpid()));

        return entity;
    }

    /**
     * Certify cause of death — requires medical practitioner authorization.
     * Transitions status from SUBMITTED to CERTIFIED.
     *
     * @throws IllegalArgumentException if notification not found
     * @throws IllegalStateException if notification is not in a certifiable state
     */
    @Transactional
    public DeathNotificationEntity certify(UUID tenantId, Long notificationId,
                                            String certifyingPractitioner, String certifierRole) {
        DeathNotificationEntity entity = deathRepository.findByTenantIdAndId(tenantId, notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Death notification not found: " + notificationId));

        if (!"SUBMITTED".equals(entity.getStatus())) {
            throw new IllegalStateException(
                    "Cannot certify death notification in status: " + entity.getStatus());
        }

        entity.setStatus("CERTIFIED");
        entity.setCertifyingPractitioner(certifyingPractitioner);
        entity.setCertifierRole(certifierRole);
        entity.setCertifiedAt(OffsetDateTime.now());
        // A practitioner certification is the highest certainty class — supersedes any probable basis.
        entity.setCauseOfDeathBasis("MEDICALLY_CERTIFIED");
        entity = deathRepository.save(entity);

        publishEvent("DEATH_NOTIFICATION", entity.getId().toString(),
                "DEATH_CERTIFIED",
                String.format("{\"notificationId\":%d,\"deceasedCpid\":\"%s\",\"certifiedBy\":\"%s\"}",
                        entity.getId(), entity.getDeceasedCpid(), certifyingPractitioner));

        return entity;
    }

    /**
     * Required fields a civil-registration package cannot be submitted without (WS#8). The cause
     * requirement is satisfied either by a medical certification (status CERTIFIED/REGISTERED) OR — for
     * a community/field death that never entered a facility — by a probable cause from verbal autopsy
     * or field investigation ({@code cause_of_death_basis}). The probable-vs-certified distinction is
     * preserved on the record; it is never flattened into "certified".
     */
    public List<String> validatePackage(DeathNotificationEntity e) {
        List<String> missing = new ArrayList<>();
        if (e.getDeceasedCpid() == null || e.getDeceasedCpid().isBlank()) missing.add("deceasedIdentity");
        if (e.getDateOfDeath() == null) missing.add("dateOfDeath");
        if (e.getPlaceOfDeathType() == null || e.getPlaceOfDeathType().isBlank()) missing.add("placeOfDeath");
        if (e.getUnderlyingCause() == null || e.getUnderlyingCause().isBlank()) missing.add("underlyingCause");
        boolean certified = "CERTIFIED".equals(e.getStatus()) || "REGISTERED".equals(e.getStatus());
        boolean probable = "VERBAL_AUTOPSY_PROBABLE".equals(e.getCauseOfDeathBasis())
                || "FIELD_INVESTIGATION_PROBABLE".equals(e.getCauseOfDeathBasis());
        if (!certified && !probable) missing.add("certification");
        return missing;
    }

    /**
     * Mark the civil-registration package ready for the Registrar General. Blocks when required
     * fields are missing. Marking ready does NOT forge a registration — it stages the handoff.
     */
    @Transactional
    public DeathNotificationEntity markPackageReady(UUID tenantId, Long notificationId) {
        DeathNotificationEntity e = deathRepository.findByTenantIdAndId(tenantId, notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Death notification not found: " + notificationId));
        List<String> missing = validatePackage(e);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Civil-registration package incomplete — missing: " + String.join(", ", missing));
        }
        e.setPackageReady(true);
        e.setPackageValidatedAt(OffsetDateTime.now());
        e = deathRepository.save(e);
        // Owner-routed hook → Registrar General electronic submission (execution deferred; we never
        // forge a completed registration). Downstream submission updates status to REGISTERED with a
        // real civil_reg_number via register().
        publishEvent("DEATH_NOTIFICATION", e.getId().toString(), "CRVS_PACKAGE_READY",
                String.format("{\"notificationId\":%d,\"deceasedCpid\":\"%s\"}", e.getId(), e.getDeceasedCpid()));
        return e;
    }

    /**
     * Record a real civil registration outcome from the Registrar General (owner-routed). REGISTERED
     * requires a civil_reg_number — we never synthesise one. May only follow a CERTIFIED notification.
     */
    @Transactional
    public DeathNotificationEntity register(UUID tenantId, Long notificationId, String civilRegNumber) {
        DeathNotificationEntity e = deathRepository.findByTenantIdAndId(tenantId, notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Death notification not found: " + notificationId));
        if (!"CERTIFIED".equals(e.getStatus())) {
            throw new IllegalStateException("Only a CERTIFIED notification may be registered; status is " + e.getStatus());
        }
        if (civilRegNumber == null || civilRegNumber.isBlank()) {
            throw new IllegalArgumentException("A civil registration number from the Registrar General is required");
        }
        e.setStatus("REGISTERED");
        e.setCivilRegNumber(civilRegNumber);
        e.setRegisteredAt(OffsetDateTime.now());
        e = deathRepository.save(e);
        publishEvent("DEATH_NOTIFICATION", e.getId().toString(), "DEATH_REGISTERED",
                String.format("{\"notificationId\":%d,\"deceasedCpid\":\"%s\",\"civilRegNumber\":\"%s\"}",
                        e.getId(), e.getDeceasedCpid(), civilRegNumber));
        return e;
    }

    /**
     * Find a single death notification by tenant and ID.
     */
    @Transactional(readOnly = true)
    public DeathNotificationEntity findById(UUID tenantId, Long id) {
        return deathRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Death notification not found: " + id));
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
