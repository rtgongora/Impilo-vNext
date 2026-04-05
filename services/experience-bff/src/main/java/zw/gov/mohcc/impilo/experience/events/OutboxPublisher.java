package zw.gov.mohcc.impilo.experience.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Polls the event_outbox table and publishes unpublished events to Kafka.
 * Uses the standard outbox pattern: write in transaction, publish async.
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafkaTemplate) {
        this.jdbc = jdbc;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<Map<String, Object>> events = jdbc.queryForList("""
            SELECT id, event_type, partition_key, payload
            FROM event_outbox
            WHERE published = FALSE
            ORDER BY id ASC
            LIMIT ?
            """, BATCH_SIZE);

        for (Map<String, Object> event : events) {
            try {
                String topic = deriveTopicFromEventType((String) event.get("event_type"));
                String key = (String) event.get("partition_key");
                String payload = (String) event.get("payload");

                kafkaTemplate.send(topic, key, payload);

                jdbc.update("UPDATE event_outbox SET published = TRUE, published_at = NOW() WHERE id = ?",
                        event.get("id"));
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}: {}", event.get("id"), e.getMessage());
                break;
            }
        }
    }

    private String deriveTopicFromEventType(String eventType) {
        // eventType format: impilo.experience.<domain>.<action>.v1
        // topic format: experience.<domain>.<action>
        if (eventType.startsWith("impilo.experience.")) {
            String suffix = eventType.substring("impilo.experience.".length());
            int versionIdx = suffix.lastIndexOf(".v");
            if (versionIdx > 0) {
                suffix = suffix.substring(0, versionIdx);
            }
            return "experience." + suffix;
        }
        return eventType;
    }
}
