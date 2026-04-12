package zw.gov.mohcc.impilo.assetregistry.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.assetregistry.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.assetregistry.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Publishes v1.1 asset outbox rows to Kafka (lifecycle + supply-plane mirror topics).
 */
@Component
@Profile("!test")
public class AssetOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(AssetOutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public AssetOutboxPublisher(OutboxEventRepository outboxRepository,
                                KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${asset-registry.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }
        for (OutboxEventEntity row : pending) {
            try {
                String topic = routeLifecycleTopic(row.getEventType());
                kafkaTemplate.send(topic, row.getPartitionKey() != null ? row.getPartitionKey() : row.getAggregateId(),
                        row.getPayloadJson());
                row.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(row);
            } catch (Exception e) {
                log.error("Failed to publish asset outbox id={} type={}: {}", row.getId(), row.getEventType(),
                        e.getMessage(), e);
            }
        }
    }

    static String routeLifecycleTopic(String eventType) {
        if (eventType == null) {
            return "asset.registry.events";
        }
        return switch (eventType) {
            case "impilo.asset.asset.created.v1" -> "asset.registered";
            case "impilo.asset.asset.maintenance.v1" -> "asset.maintenance";
            case "impilo.asset.asset.retired.v1" -> "asset.retired";
            case "impilo.asset.asset.updated.v1",
                 "impilo.asset.asset.status.changed.v1" -> "asset.registry.events";
            default -> "asset.registry.events";
        };
    }
}
