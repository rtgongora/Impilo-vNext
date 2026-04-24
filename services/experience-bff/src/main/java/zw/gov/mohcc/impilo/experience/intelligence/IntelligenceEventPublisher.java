package zw.gov.mohcc.impilo.experience.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes Health Intelligence plane lifecycle events to Kafka when a template is available.
 * Event types align with the platform intelligence contract (query, summary, recommendation, etc.).
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
public class IntelligenceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(IntelligenceEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public IntelligenceEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${impilo.intelligence.events-topic:impilo.intelligence.events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(String eventType, Map<String, Object> payload) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventType", eventType);
            envelope.put("payload", payload);
            envelope.put("occurredAt", java.time.Instant.now().toString());
            String json = objectMapper.writeValueAsString(envelope);
            String key = String.valueOf(payload.getOrDefault("correlationId", payload.getOrDefault("queryId", "na")));
            kafkaTemplate.send(topic, key, json);
        } catch (Exception e) {
            log.warn("Failed to publish intelligence event {}: {}", eventType, e.getMessage());
        }
    }
}
