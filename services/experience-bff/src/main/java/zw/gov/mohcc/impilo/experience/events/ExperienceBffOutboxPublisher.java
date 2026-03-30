package zw.gov.mohcc.impilo.experience.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Polls the BFF {@code event_outbox} table and publishes pending events to Kafka.
 *
 * <p>Each event is wrapped in an EventEnvelope that includes
 * {@code source_service = "experience-bff"} so that any downstream Kafka consumer
 * (including the BFF's own inbound consumers) can detect and discard BFF-originated
 * events — preventing self-consumption loops.</p>
 *
 * <p>Topic naming convention: {@code experience.<domain>} derived from the
 * event_type stored in the outbox (e.g. {@code impilo.experience.allergy.recorded.v1}
 * → {@code experience.allergy}).</p>
 */
@Component
public class ExperienceBffOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ExperienceBffOutboxPublisher.class);
    private static final String SOURCE_SERVICE = "experience-bff";

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ExperienceBffOutboxPublisher(JdbcTemplate jdbc,
                                        KafkaTemplate<String, String> kafkaTemplate,
                                        ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${experience.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, event_id, event_type, schema_version,
                       correlation_id, causation_id, idempotency_key,
                       producer, tenant_id, pod_id,
                       subject_type, subject_id, payload, meta,
                       occurred_at, partition_key
                FROM event_outbox
                WHERE published = FALSE
                ORDER BY id ASC
                LIMIT 100
                """);

        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String eventType = (String) row.get("event_type");
            String partitionKey = (String) row.get("partition_key");

            try {
                String envelope = buildEnvelope(row);
                String topic = resolveTopic(eventType);

                kafkaTemplate.send(topic, partitionKey, envelope);

                jdbc.update(
                        "UPDATE event_outbox SET published = TRUE, published_at = ? WHERE id = ?",
                        OffsetDateTime.now(), id);

                log.debug("ExperienceBffOutboxPublisher: published id={} eventType={} topic={}", id, eventType, topic);

            } catch (Exception e) {
                log.error("ExperienceBffOutboxPublisher: failed to publish outbox id={} eventType={}: {}",
                        id, eventType, e.getMessage(), e);
                break;
            }
        }
    }

    private String buildEnvelope(Map<String, Object> row) throws Exception {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event_id", row.get("event_id").toString());
        envelope.put("event_type", row.get("event_type"));
        envelope.put("schema_version", row.get("schema_version"));
        envelope.put("source_service", SOURCE_SERVICE);
        envelope.put("correlation_id", row.get("correlation_id"));
        envelope.put("causation_id", row.get("causation_id"));
        envelope.put("idempotency_key", row.get("idempotency_key"));
        envelope.put("tenant_id", row.get("tenant_id"));
        envelope.put("pod_id", row.get("pod_id"));
        envelope.put("subject_type", row.get("subject_type"));
        envelope.put("subject_id", row.get("subject_id"));
        envelope.put("partition_key", row.get("partition_key"));
        envelope.put("occurred_at", row.get("occurred_at").toString());

        Object payloadObj = row.get("payload");
        envelope.put("payload", payloadObj != null
                ? objectMapper.readTree(payloadObj.toString())
                : Map.of());

        return objectMapper.writeValueAsString(envelope);
    }

    /**
     * Derives a Kafka topic from the event type.
     *
     * <p>Convention: {@code impilo.experience.<domain>.<verb>.<version>}
     * maps to {@code experience.<domain>}. Falls back to {@code experience.events}.</p>
     */
    static String resolveTopic(String eventType) {
        if (eventType == null) return "experience.events";
        if (eventType.contains("patient"))    return "experience.patient";
        if (eventType.contains("allergy"))    return "experience.allergy";
        if (eventType.contains("encounter"))  return "experience.encounter";
        if (eventType.contains("vitals") || eventType.contains("vital")) return "experience.vitals";
        if (eventType.contains("queue"))      return "experience.queue";
        if (eventType.contains("shift"))      return "experience.shift";
        if (eventType.contains("referral"))   return "experience.referral";
        if (eventType.contains("reminder"))   return "experience.reminder";
        return "experience.events";
    }
}
