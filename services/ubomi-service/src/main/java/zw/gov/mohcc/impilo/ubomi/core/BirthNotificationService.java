package zw.gov.mohcc.impilo.ubomi.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ubomi.integration.VitoClient;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.BirthNotificationEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.BirthNotificationRepository;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class BirthNotificationService {

    private final BirthNotificationRepository birthRepository;
    private final EventOutboxRepository outboxRepository;
    private final VitoClient vitoClient;

    public BirthNotificationService(BirthNotificationRepository birthRepository,
                                     EventOutboxRepository outboxRepository,
                                     VitoClient vitoClient) {
        this.birthRepository = birthRepository;
        this.outboxRepository = outboxRepository;
        this.vitoClient = vitoClient;
    }

    @Transactional(readOnly = true)
    public Page<BirthNotificationEntity> list(UUID tenantId, int page, int size) {
        return birthRepository.findByTenantId(tenantId, PageRequest.of(page, size));
    }

    @Transactional
    public BirthNotificationEntity submit(BirthNotificationEntity entity) {
        entity.setStatus("SUBMITTED");
        entity = birthRepository.save(entity);

        publishEvent("BIRTH_NOTIFICATION", entity.getId().toString(),
                "BIRTH_SUBMITTED",
                Map.of(
                        "notificationId", entity.getId(),
                        "notificationNumber", entity.getNotificationNumber(),
                        "tenantId", entity.getTenantId().toString()
                ));

        return entity;
    }

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
                Map.of(
                        "notificationId", entity.getId(),
                        "notificationNumber", entity.getNotificationNumber(),
                        "tenantId", entity.getTenantId().toString(),
                        "motherCpid", entity.getMotherCpid() != null ? entity.getMotherCpid() : ""
                ));

        if (entity.getMotherCpid() != null) {
            vitoClient.notifyBirthRegistration(
                    entity.getMotherCpid(),
                    entity.getNotificationNumber(),
                    entity.getTenantId().toString()
            );
        }

        return entity;
    }

    @Transactional(readOnly = true)
    public BirthNotificationEntity findById(UUID tenantId, Long id) {
        return birthRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Birth notification not found: " + id));
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, Map<String, Object> payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
