package zw.gov.mohcc.impilo.daidzai.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * No-broker outbox drainer (pattern from {@code OrgRegistryOutboxNoKafkaDrainer}). Active only when
 * {@code daidzai.kafka-events-enabled=false} — local/dev/CI and the trauma runtime-proof rigs, where
 * there is no Kafka. Domain events (including {@code daidzai.ems.*}) are still written to the outbox
 * transactionally by {@link DaidzaiEventEmitter}, so they are provable as {@code dai_outbox} rows;
 * this drainer simply marks them published so the table does not grow unbounded and the
 * {@link DaidzaiOutboxPublisher} Kafka relay (disabled in this mode) never error-loops.
 *
 * <p>Defaults OFF (prod publishes to Kafka).</p>
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "daidzai.kafka-events-enabled", havingValue = "false")
public class DaidzaiOutboxNoKafkaDrainer {

    private static final Logger log = LoggerFactory.getLogger(DaidzaiOutboxNoKafkaDrainer.class);

    private final EventOutboxRepository outboxRepository;

    public DaidzaiOutboxNoKafkaDrainer(EventOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(fixedDelayString = "${daidzai.outbox.poll-interval-ms:2000}")
    @Transactional
    public void drain() {
        List<EventOutboxEntity> pending = outboxRepository.findUnpublished(PageRequest.of(0, 100));
        if (pending.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (EventOutboxEntity row : pending) {
            log.debug("Kafka disabled — marking daidzai outbox event published: id={} type={} aggregate={}/{}",
                    row.getId(), row.getEventType(), row.getAggregateType(), row.getAggregateId());
            row.setPublishedAt(now);
            outboxRepository.save(row);
        }
        log.info("no-Kafka drainer marked {} daidzai outbox event(s) published", pending.size());
    }
}
