package zw.gov.mohcc.impilo.ubomi.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.BirthNotificationEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.BirthNotificationRepository;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Business logic for birth notifications.
 *
 * Lifecycle: SUBMITTED -> VALIDATED -> REGISTERED (or REJECTED / CANCELLED)
 *
 * On REGISTERED, publishes a BIRTH_REGISTERED event via the outbox pattern
 * so that VITO can issue the newborn's Impilo ID.
 */
@Service
public class BirthNotificationService {

    private final BirthNotificationRepository birthRepository;
    private final EventOutboxRepository outboxRepository;

    public BirthNotificationService(BirthNotificationRepository birthRepository,
                                     EventOutboxRepository outboxRepository) {
        this.birthRepository = birthRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Paginated listing of birth notifications for a tenant.
     */
    @Transactional(readOnly = true)
    public Page<BirthNotificationEntity> list(UUID tenantId, int page, int size) {
        return birthRepository.findByTenantId(tenantId, PageRequest.of(page, size));
    }

    /**
     * Submit a new birth notification.
     * Sets initial status to SUBMITTED and publishes a BIRTH_SUBMITTED event.
     */
    @Transactional
    public BirthNotificationEntity submit(BirthNotificationEntity entity) {
        entity.setStatus("SUBMITTED");
        entity = birthRepository.save(entity);

        publishEvent("BIRTH_NOTIFICATION", entity.getId().toString(),
                "BIRTH_SUBMITTED",
                String.format("{\"notificationId\":%d,\"notificationNumber\":\"%s\",\"tenantId\":\"%s\"}",
                        entity.getId(), entity.getNotificationNumber(), entity.getTenantId()));

        return entity;
    }

    /**
     * Approve a birth notification, transitioning it to REGISTERED.
     * Publishes BIRTH_REGISTERED event so VITO can issue the newborn's Impilo ID.
     *
     * @throws IllegalArgumentException if notification not found
     * @throws IllegalStateException if notification is not in an approvable state
     */
    @Transactional
    public BirthNotificationEntity approve(UUID tenantId, Long notificationId) {
        BirthNotificationEntity entity = birthRepository.findByTenantIdAndId(tenantId, notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Birth notification not found: " + notificationId));

        if (!"SUBMITTED".equals(entity.getStatus()) && !"VALIDATED".equals(entity.getStatus())) {
            throw new IllegalStateException(
                    "Cannot approve birth notification in status: " + entity.getStatus());
        }

        entity.setStatus("REGISTERED");
        entity.setRegisteredAt(OffsetDateTime.now());
        entity = birthRepository.save(entity);

        publishEvent("BIRTH_NOTIFICATION", entity.getId().toString(),
                "BIRTH_REGISTERED",
                String.format("{\"notificationId\":%d,\"notificationNumber\":\"%s\",\"tenantId\":\"%s\",\"motherCpid\":\"%s\"}",
                        entity.getId(), entity.getNotificationNumber(),
                        entity.getTenantId(), entity.getMotherCpid()));

        return entity;
    }

    /**
     * Find a single birth notification by tenant and ID.
     */
    @Transactional(readOnly = true)
    public BirthNotificationEntity findById(UUID tenantId, Long id) {
        return birthRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Birth notification not found: " + id));
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
