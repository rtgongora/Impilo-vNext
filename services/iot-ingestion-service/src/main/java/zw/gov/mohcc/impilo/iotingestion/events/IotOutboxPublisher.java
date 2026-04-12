package zw.gov.mohcc.impilo.iotingestion.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.iotingestion.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.iotingestion.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Publishes IoT ingestion outbox rows to the telemetry Kafka bus.
 */
@Component
@Profile("!test")
public class IotOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(IotOutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public IotOutboxPublisher(OutboxEventRepository outboxRepository,
                              KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${iot-ingestion.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }
        for (OutboxEventEntity row : pending) {
            try {
                String topic = routeTelemetryTopic(row.getEventType());
                String key = row.getPartitionKey() != null ? row.getPartitionKey() : row.getAggregateId();
                kafkaTemplate.send(topic, key, row.getPayloadJson());
                row.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(row);
            } catch (Exception e) {
                log.error("Failed to publish IoT outbox id={} type={}: {}", row.getId(), row.getEventType(),
                        e.getMessage(), e);
            }
        }
    }

    static String routeTelemetryTopic(String eventType) {
        if (eventType == null) {
            return "telemetry.iot.device.reading";
        }
        return switch (eventType) {
            case "impilo.iot.telemetry.reading.ingested.v1" -> "telemetry.iot.device.reading";
            default -> "telemetry.iot.device.reading";
        };
    }
}
