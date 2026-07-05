package zw.gov.mohcc.impilo.orgregistry.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * No-Kafka contexts ({@code impilo.orgregistry.kafka-events-enabled=false},
 * the default): drains the outbox by marking rows published and logging them,
 * so the outbox table does not grow unbounded in local/dev/CI runs.
 */
@Component
@ConditionalOnProperty(name = "impilo.orgregistry.kafka-events-enabled",
        havingValue = "false", matchIfMissing = true)
public class OrgRegistryOutboxNoKafkaDrainer {

    private static final Logger log = LoggerFactory.getLogger(OrgRegistryOutboxNoKafkaDrainer.class);

    private final EventOutboxRepository outboxRepository;

    public OrgRegistryOutboxNoKafkaDrainer(EventOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(fixedDelayString = "${impilo.orgregistry.outbox.poll-interval-ms:2000}")
    @Transactional
    public void drain() {
        List<EventOutboxEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
        if (pending.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (EventOutboxEntity row : pending) {
            log.info("Kafka disabled — marking org-registry outbox event published: id={} type={} aggregate={}/{}",
                    row.getId(), row.getEventType(), row.getAggregateType(), row.getAggregateId());
            row.setPublishedAt(now);
            outboxRepository.save(row);
        }
    }
}
