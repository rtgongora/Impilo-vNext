package zw.gov.mohcc.impilo.experience.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnBean(KafkaTemplate.class)
public class TelemedicineCommunicationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TelemedicineCommunicationEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public TelemedicineCommunicationEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${impilo.telemedicine.communication.events-topic:clinical.telemedicine.communication}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(String eventType, String correlationKey, Map<String, Object> payload) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("eventType", eventType);
            envelope.put("occurredAt", Instant.now().toString());
            envelope.put("payload", payload == null ? Map.of() : payload);
            kafkaTemplate.send(topic, correlationKey == null ? "telemedicine" : correlationKey, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.warn("Unable to publish telemedicine communication event {}: {}", eventType, e.getMessage());
        }
    }
}
