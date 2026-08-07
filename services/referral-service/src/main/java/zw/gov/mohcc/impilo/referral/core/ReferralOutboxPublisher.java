package zw.gov.mohcc.impilo.referral.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.referral.events.ReferralOutboxRow;
import zw.gov.mohcc.impilo.referral.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.referral.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes referral outbox rows via dual emit: the legacy topic keeps the raw domain payload
 * it has always carried, and the v1.1 topic carries the full EventEnvelope.
 *
 * <p>Dual emit is what makes this conversion safe. Legacy topics are service-named
 * ({@code referral.surgical.accepted}); v1.1 topics are always {@code impilo.referral.*}. The
 * namespaces cannot collide, so {@code SchedulingReferralConsumer} keeps receiving exactly the
 * bytes it received before while federation metadata starts flowing on the new topic.</p>
 */
@Component
public class ReferralOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReferralOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReferralOutboxPublisher(EventOutboxRepository outboxRepository,
                                   ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
                                   @Value("${referral.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("referral"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        log.info("ReferralOutboxPublisher initialised with emit-mode={}", effectiveEmitMode());
    }

    /**
     * Append a domain event. The tenant is passed explicitly rather than read from ambient
     * state so that a row can never be written without one.
     */
    @Transactional
    public void append(String aggregateType,
                       String aggregateId,
                       String eventType,
                       UUID tenantId,
                       String subjectType,
                       String subjectId,
                       Map<String, Object> payload) {
        if (tenantId == null) {
            throw new IllegalArgumentException(
                    "referral outbox append requires a tenant: " + aggregateType + "/" + eventType);
        }
        outboxRepository.save(EventOutboxEntity.create(
                aggregateType, aggregateId, eventType, tenantId,
                subjectType, subjectId, serialize(payload)));
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload != null ? payload : Map.of());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("referral outbox payload is not serialisable", e);
        }
    }

    @Scheduled(fixedDelayString = "${referral.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int published = publishPendingEvents();
        if (published > 0) {
            log.info("Published {} referral outbox events", published);
        }
    }

    @Override
    protected List<? extends OutboxRow> fetchUnpublished() {
        return outboxRepository.findUnpublished(PageRequest.of(0, 100))
                .stream()
                .map(EventOutboxEntity::toOutboxRow)
                .toList();
    }

    @Override
    protected void sendToKafka(String topic, String key, String value) {
        KafkaTemplate<String, String> kafka = kafkaTemplateProvider.getIfAvailable();
        if (kafka == null) {
            // No broker wired in this profile. Returning quietly would mark the row published
            // without it ever leaving, so refuse and let the drain retry.
            throw new IllegalStateException("KafkaTemplate unavailable; refusing to drop event for " + topic);
        }
        kafka.send(topic, key, value);
    }

    @Override
    protected void markPublished(OutboxRow row, OffsetDateTime publishedAt) {
        outboxRepository.findById(row.id()).ifPresent(entity -> {
            entity.setPublishedAt(publishedAt);
            outboxRepository.save(entity);
        });
    }

    @Override
    protected void markFailed(OutboxRow row, String errorMessage) {
        outboxRepository.findById(row.id()).ifPresent(entity -> {
            entity.setPublishError(errorMessage);
            entity.setRetryCount(row.retryCount() + 1);
            outboxRepository.save(entity);
        });
    }

    /**
     * The legacy topic is the stored event type itself, which is how referral has always
     * routed. Internal UPPER_SNAKE lifecycle events were never published and still are not.
     */
    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        if (row instanceof ReferralOutboxRow referralRow && referralRow.hasLegacyTopic()) {
            return referralRow.legacyEventType();
        }
        return null;
    }
}
