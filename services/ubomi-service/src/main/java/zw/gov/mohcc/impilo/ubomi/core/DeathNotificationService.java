package zw.gov.mohcc.impilo.ubomi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ubomi.integration.VitoClient;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.DeathNotificationEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.DeathNotificationRepository;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class DeathNotificationService {

    private static final Logger log = LoggerFactory.getLogger(DeathNotificationService.class);
    private static final String TRUST_REVOCATION_IDENTITY_TOPIC = "trust.revocation.identity";

    private final DeathNotificationRepository deathRepository;
    private final EventOutboxRepository outboxRepository;
    private final VitoClient vitoClient;
    private final KafkaTemplate<String, String> trustChannelKafkaTemplate;

    public DeathNotificationService(DeathNotificationRepository deathRepository,
                                     EventOutboxRepository outboxRepository,
                                     VitoClient vitoClient,
                                     @Qualifier("trustChannelKafkaTemplate") KafkaTemplate<String, String> trustChannelKafkaTemplate) {
        this.deathRepository = deathRepository;
        this.outboxRepository = outboxRepository;
        this.vitoClient = vitoClient;
        this.trustChannelKafkaTemplate = trustChannelKafkaTemplate;
    }

    @Transactional(readOnly = true)
    public Page<DeathNotificationEntity> list(UUID tenantId, int page, int size) {
        return deathRepository.findByTenantId(tenantId, PageRequest.of(page, size));
    }

    @Transactional
    public DeathNotificationEntity submit(DeathNotificationEntity entity) {
        entity.setStatus("SUBMITTED");
        entity = deathRepository.save(entity);

        publishEvent("DEATH_NOTIFICATION", entity.getId().toString(),
                "DEATH_SUBMITTED",
                Map.of(
                        "notificationId", entity.getId(),
                        "notificationNumber", entity.getNotificationNumber(),
                        "tenantId", entity.getTenantId().toString(),
                        "deceasedCpid", entity.getDeceasedCpid()
                ));

        return entity;
    }

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
                Map.of(
                        "notificationId", entity.getId(),
                        "deceasedCpid", entity.getDeceasedCpid(),
                        "certifiedBy", certifyingPractitioner
                ));

        return entity;
    }

    @Transactional
    public DeathNotificationEntity register(UUID tenantId, Long notificationId) {
        DeathNotificationEntity entity = deathRepository.findByTenantIdAndId(tenantId, notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Death notification not found: " + notificationId));

        if (!"CERTIFIED".equals(entity.getStatus())) {
            throw new IllegalStateException(
                    "Cannot register death notification in status: " + entity.getStatus());
        }

        entity.setStatus("REGISTERED");
        entity.setRegisteredAt(OffsetDateTime.now());
        entity = deathRepository.save(entity);

        publishEvent("DEATH_NOTIFICATION", entity.getId().toString(),
                "DEATH_REGISTERED",
                Map.of(
                        "notificationId", entity.getId(),
                        "notificationNumber", entity.getNotificationNumber(),
                        "tenantId", entity.getTenantId().toString(),
                        "deceasedCpid", entity.getDeceasedCpid()
                ));

        publishToTrustRevocationChannel(entity);

        vitoClient.notifyDeath(
                entity.getDeceasedCpid(),
                entity.getNotificationNumber(),
                entity.getTenantId().toString()
        );

        return entity;
    }

    @Transactional(readOnly = true)
    public DeathNotificationEntity findById(UUID tenantId, Long id) {
        return deathRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Death notification not found: " + id));
    }

    private void publishToTrustRevocationChannel(DeathNotificationEntity entity) {
        String payload = String.format(
                "{\"eventType\":\"impilo.ubomi.identity.revoked.v1\",\"deceasedCpid\":\"%s\",\"tenantId\":\"%s\",\"notificationNumber\":\"%s\",\"occurredAt\":\"%s\"}",
                entity.getDeceasedCpid(),
                entity.getTenantId(),
                entity.getNotificationNumber(),
                OffsetDateTime.now()
        );
        trustChannelKafkaTemplate.send(TRUST_REVOCATION_IDENTITY_TOPIC, entity.getDeceasedCpid(), payload);
        log.info("Published identity revocation to trust channel for deceasedCpid={}", entity.getDeceasedCpid());
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
