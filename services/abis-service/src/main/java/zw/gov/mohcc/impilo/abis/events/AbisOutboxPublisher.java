package zw.gov.mohcc.impilo.abis.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.abis.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.abis.persistence.repository.EventOutboxRepository;

import java.time.Instant;
import java.util.List;

/**
 * ABIS event outbox publisher (standard Impilo outbox pattern).
 *
 * <p>Payloads are pre-serialized JSON strings, sent verbatim — the producer is configured
 * with StringSerializer for key AND value (estate law: a JsonSerializer would double-encode
 * the pre-serialized payload into a quoted string literal and consumers would silently drop it).</p>
 */
@Component
public class AbisOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(AbisOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public AbisOutboxPublisher(EventOutboxRepository outboxRepository,
                               KafkaTemplate<String, String> kafkaTemplate,
                               @Value("${abis.events.kafka-topic:platform.abis.events}") String topic) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${abis.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        List<EventOutboxEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (EventOutboxEntity event : pending) {
            kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
            event.setPublishedAt(Instant.now());
            outboxRepository.save(event);
        }
        if (!pending.isEmpty()) {
            log.info("Published {} ABIS outbox events to {}", pending.size(), topic);
        }
    }
}
