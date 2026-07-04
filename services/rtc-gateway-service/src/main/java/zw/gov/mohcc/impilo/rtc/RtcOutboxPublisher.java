package zw.gov.mohcc.impilo.rtc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rtc.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.rtc.persistence.repository.EventOutboxRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Transactional outbox for rtc events. With a Kafka template present (the
 * estate default), each event publishes to its event-typed topic — the
 * {@code impilo.rtc.*.v1} names emitted by the webhook translator ARE topic
 * names, matching how learning/live/khuluma events travel. Without Kafka
 * (thin test contexts) events are marked published and logged, preserving
 * the old deferred behaviour.
 */
@Component
public class RtcOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(RtcOutboxPublisher.class);

    /** Events whose type is not topic-shaped land here (legacy RTC_SESSION_* types). */
    private static final String DEFAULT_TOPIC = "platform.rtc.events";

    private final EventOutboxRepository outboxRepository;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;

    public RtcOutboxPublisher(EventOutboxRepository outboxRepository,
                              ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
                              ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.objectMapper = objectMapper;
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

    @Scheduled(fixedDelayString = "${rtc.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        List<EventOutboxEntity> pending = outboxRepository.findUnpublished(PageRequest.of(0, 100));
        if (pending.isEmpty()) {
            return;
        }
        KafkaTemplate<String, String> kafka = kafkaTemplateProvider.getIfAvailable();
        Instant now = Instant.now();
        int sent = 0;
        for (EventOutboxEntity event : pending) {
            if (kafka != null) {
                try {
                    kafka.send(topicFor(event.getEventType()), event.getAggregateId(), serialize(event));
                    sent++;
                } catch (Exception e) {
                    // Leave unpublished; the next poll retries. Fail loud in logs.
                    log.warn("rtc outbox publish failed for event {} ({}): {}",
                            event.getId(), event.getEventType(), e.getMessage());
                    continue;
                }
            }
            event.setPublishedAt(now);
            outboxRepository.save(event);
        }
        if (kafka != null) {
            log.info("Published {}/{} rtc outbox events to Kafka", sent, pending.size());
        } else {
            log.info("Marked {} rtc outbox events published (no Kafka template in context)", pending.size());
        }
    }

    private static String topicFor(String eventType) {
        return eventType != null && eventType.startsWith("impilo.") ? eventType : DEFAULT_TOPIC;
    }

    private String serialize(EventOutboxEntity event) {
        try {
            return objectMapper.writeValueAsString(event.getPayload());
        } catch (Exception e) {
            return "{}";
        }
    }
}
