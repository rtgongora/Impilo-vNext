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
        entity = deathRepository.save(entity);

        publishEvent("DEATH_NOTIFICATION", entity.getId().toString(),
                "DEATH_CERTIFIED",
                String.format("{\"notificationId\":%d,\"deceasedCpid\":\"%s\",\"certifiedBy\":\"%s\"}",
                        entity.getId(), entity.getDeceasedCpid(), certifyingPractitioner));

        return entity;
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
