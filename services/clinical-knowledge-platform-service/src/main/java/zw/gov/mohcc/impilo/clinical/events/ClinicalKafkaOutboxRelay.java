package zw.gov.mohcc.impilo.clinical.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.clinical.persistence.entity.ClinicalEventOutboxEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.ClinicalEventOutboxRepository;

import java.time.Instant;

/**
 * Polls {@code clinical.event_outbox} and publishes JSON payloads to Kafka when relay is enabled.
 */
@Component
@ConditionalOnProperty(prefix = "impilo.clinical.kafka", name = "relay-enabled", havingValue = "true")
public class ClinicalKafkaOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(ClinicalKafkaOutboxRelay.class);

    private final ClinicalEventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ClinicalKafkaOutboxRelay(
            ClinicalEventOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${impilo.clinical.kafka.relay-poll-ms:2000}")
    @Transactional
    public void publishPending() {
        for (ClinicalEventOutboxEntity row : outboxRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc()) {
            try {
                String topic = ClinicalKafkaTopics.topicForEventType(row.getEventType());
                String key = row.getSubjectId() != null ? row.getSubjectId() : row.getId().toString();
                String body = objectMapper.writeValueAsString(row.getPayload());
                kafkaTemplate.send(topic, key, body);
                row.setPublishedAt(Instant.now());
                outboxRepository.save(row);
            } catch (JsonProcessingException ex) {
                log.error("Outbox row {} serialization failed: {}", row.getId(), ex.getMessage());
                break;
            } catch (Exception ex) {
                log.warn("Kafka publish failed for outbox {}: {}", row.getId(), ex.getMessage());
                break;
            }
        }
    }
}
