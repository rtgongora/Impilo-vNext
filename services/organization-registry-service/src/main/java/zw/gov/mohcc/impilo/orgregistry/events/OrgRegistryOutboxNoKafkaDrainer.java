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
import java.util.Set;

/**
 * No-Kafka contexts ({@code impilo.orgregistry.kafka-events-enabled=false},
 * the default): drains the outbox by marking rows published and logging them,
 * so the outbox table does not grow unbounded in local/dev/CI runs.
 *
 * <p><b>Work Context Resolution programme, Phase C (revocation fix):</b>
 * {@code tshepo-identity}'s {@code RegulatoryAppointmentEndedTokenConsumer}
 * exists to revoke a regulator's live duty token the moment their appointment
 * ends — but it only ever runs when Kafka is enabled. In every environment
 * that runs with the default (false, including preview today), this drainer
 * marks the SAME "appointment ended" event published without ever notifying
 * identity, so a revoked regulator's session silently keeps working until
 * its 15-minute TTL expires on its own. A revocation path that swallows
 * revocations reads as covered when it is not. This does NOT flip the global
 * Kafka default (a broader, higher-blast-radius decision — every environment
 * that relies on the false default for local/CI runs without a broker would
 * break) — it makes the specific gap for security-critical events LOUD
 * instead of silent, so it shows up in logs/alerting rather than only in an
 * audit years later.</p>
 */
@Component
@ConditionalOnProperty(name = "impilo.orgregistry.kafka-events-enabled",
        havingValue = "false", matchIfMissing = true)
public class OrgRegistryOutboxNoKafkaDrainer {

    private static final Logger log = LoggerFactory.getLogger(OrgRegistryOutboxNoKafkaDrainer.class);

    /**
     * Aggregate types whose events are security-critical downstream (identity
     * revocation, policy narrowing) — draining one of these without Kafka
     * means a real consumer never runs, not merely a missed notification.
     */
    private static final Set<String> SECURITY_CRITICAL_AGGREGATE_TYPES = Set.of("REGULATORY_APPOINTMENT");

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
            if (SECURITY_CRITICAL_AGGREGATE_TYPES.contains(row.getAggregateType())) {
                log.error("Kafka disabled — a SECURITY-CRITICAL org-registry event was drained WITHOUT "
                                + "reaching its consumer (tshepo-identity's duty-token revocation on "
                                + "appointment end/revoke never ran): id={} type={} aggregate={}/{}. "
                                + "Set ORG_REGISTRY_KAFKA_EVENTS_ENABLED=true, or revoke the affected "
                                + "session manually, in any environment where this must be enforced.",
                        row.getId(), row.getEventType(), row.getAggregateType(), row.getAggregateId());
            } else {
                log.info("Kafka disabled — marking org-registry outbox event published: id={} type={} aggregate={}/{}",
                        row.getId(), row.getEventType(), row.getAggregateType(), row.getAggregateId());
            }
            row.setPublishedAt(now);
            outboxRepository.save(row);
        }
    }
}
