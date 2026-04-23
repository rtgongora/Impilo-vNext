package zw.gov.mohcc.impilo.experience.intake;

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
 * Publishes registry intake lifecycle signals to Kafka when a template is available.
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
public class RegistryIntakeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RegistryIntakeEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public RegistryIntakeEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${impilo.registry-intake.events-topic:impilo.registry.intake}") String topic) {
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
            String key = String.valueOf(payload.getOrDefault("jobId", payload.getOrDefault("sessionId", "na")));
            kafkaTemplate.send(topic, key, json);
        } catch (Exception e) {
            log.warn("Failed to publish registry intake event {}: {}", eventType, e.getMessage());
        }
    }
}
