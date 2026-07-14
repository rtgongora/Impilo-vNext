package zw.gov.mohcc.impilo.referral.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.referral.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.referral.persistence.repository.EventOutboxRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReferralOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReferralOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReferralOutboxPublisher(EventOutboxRepository outboxRepository,
                                   ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
    }

    @Transactional
    public void append(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    @Scheduled(fixedDelayString = "${referral.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        List<EventOutboxEntity> pending = outboxRepository.findUnpublished(PageRequest.of(0, 100));
        if (pending.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        KafkaTemplate<String, String> kafka = kafkaTemplateProvider.getIfAvailable();
        for (EventOutboxEntity event : pending) {
            // Dotted event types (e.g. referral.surgical.accepted) are wire-contract
            // topics — publish them so downstream services (scheduling) can consume.
            // Legacy UPPER_SNAKE lifecycle events remain internal-only.
            if (kafka != null && event.getEventType() != null && event.getEventType().contains(".")) {
                publishToKafka(kafka, event);
            }
            event.setPublishedAt(now);
            outboxRepository.save(event);
        }
        log.info("Published {} referral outbox events", pending.size());
    }

    private void publishToKafka(KafkaTemplate<String, String> kafka, EventOutboxEntity event) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("event_type", event.getEventType());
            envelope.put("aggregate_type", event.getAggregateType());
            envelope.put("aggregate_id", event.getAggregateId());
            envelope.put("payload", event.getPayload());
            String json = objectMapper.writeValueAsString(envelope);
            kafka.send(event.getEventType(), event.getAggregateId(), json);
        } catch (Exception e) {
            log.warn("Referral outbox Kafka publish failed for {} ({}): {}",
                    event.getEventType(), event.getAggregateId(), e.getMessage());
        }
    }
}
