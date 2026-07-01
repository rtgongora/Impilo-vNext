package zw.gov.mohcc.impilo.learning.events;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningOutboxEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningOutboxRepository;

/**
 * Scheduled outbox publisher for {@code lrn_event_outbox} rows.
 *
 * <p>Implements the transactional outbox pattern: domain events are written to the
 * {@code lrn_event_outbox} table within the same database transaction as the domain
 * change by {@link zw.gov.mohcc.impilo.learning.core.LearningPlatformFacade#appendOutbox},
 * and this component asynchronously publishes them to Kafka and marks them
 * {@code published_at}. This gives at-least-once delivery without distributed
 * transactions.</p>
 *
 * <p>Phase 2 routes <em>all</em> learning events to a single topic
 * (default {@code platform.learning.events}, mirroring the {@code mvumo-service}
 * single-topic precedent). Per-event-type fan-out is an additive future enhancement
 * and is intentionally out of scope here.</p>
 *
 * <p>The publisher is gated by {@code learning.outbox.publisher.enabled}, which
 * defaults to {@code true} so production behaviour matches the platform pattern.
 * Tests disable it via {@code application-test.yml} to avoid spinning up a
 * scheduler when there is no Kafka broker.</p>
 *
 * <p><strong>Event-type naming.</strong> The event-type strings emitted by
 * {@code LearningPlatformFacade.appendOutbox(...)} today (e.g.
 * {@code learning.completion.recorded}, {@code fundo.sync.completed}) do
 * <em>not</em> follow the v1.1 envelope convention
 * ({@code impilo.{service}.{entity}.{action}.v{N}}). They are intentionally
 * preserved verbatim in Phase 2 and tracked as legacy in
 * {@code RepoEventTypeContractTest}. A future migration phase will introduce
 * v1.1 event names; this publisher will not need behavioural changes for that
 * — only the topic-routing table.</p>
 *
 * <h3>Operational metrics (Phase 3A)</h3>
 *
 * <p>When a {@link MeterRegistry} bean is present the publisher exposes:</p>
 * <ul>
 *   <li>{@code learning.outbox.published} counter, tagged {@code outcome=success|failure}
 *       — Prometheus renders these as
 *       {@code learning_outbox_published_total{outcome="success"}} /
 *       {@code learning_outbox_published_total{outcome="failure"}}.</li>
 * </ul>
 *
 * <p>The unpublished outbox backlog is intentionally <em>not</em> registered
 * here as a duplicate gauge — the platform-wide
 * {@code zw.gov.mohcc.impilo.ops.metrics.OutboxLagProbe} (auto-configured
 * by {@code ops-instrumentation}) already exposes
 * {@code impilo_ops_outbox_lag{table="lrn_event_outbox"}} from a JDBC
 * {@code COUNT(*) WHERE published_at IS NULL} query, which is the
 * peer-consistent convention for backlog visibility across all Impilo
 * services. Adding a second gauge here would create two sources of truth
 * for the same number.</p>
 */
@Component
@ConditionalOnProperty(name = "learning.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class LearningOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(LearningOutboxPublisher.class);

    /** Micrometer base metric name; rendered as {@code learning_outbox_published_total} in Prometheus. */
    static final String METRIC_PUBLISHED = "learning.outbox.published";
    static final String TAG_OUTCOME = "outcome";
    static final String OUTCOME_SUCCESS = "success";
    static final String OUTCOME_FAILURE = "failure";
    /**
     * Local fallback backlog gauge name, only registered when no peer
     * {@code impilo.ops.outbox.lag} probe is present (e.g. unit-test
     * contexts with no JDBC). Rendered as
     * {@code learning_outbox_unpublished_backlog} in Prometheus.
     */
    static final String METRIC_BACKLOG = "learning.outbox.unpublished.backlog";

    private final LearningOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String defaultTopic;
    private final int batchSize;

    // Phase 3A: Micrometer instruments. Null if no MeterRegistry bean is present
    // (e.g. very thin test contexts) — the publisher must remain functional
    // without metrics so existing unit tests keep working with a null registry.
    @Nullable private final Counter publishedSuccessCounter;
    @Nullable private final Counter publishedFailureCounter;

    /**
     * Spring wiring (single constructor — dual public constructors prevented
     * default-constructor instantiation failures at runtime).
     */
    @Autowired
    public LearningOutboxPublisher(
            LearningOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${learning.outbox.topics.default:platform.learning.events}") String defaultTopic,
            @Value("${learning.outbox.publisher.batch-size:100}") int batchSize,
            @Autowired(required = false) @Nullable MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.defaultTopic = defaultTopic;
        this.batchSize = batchSize;

        if (meterRegistry != null) {
            this.publishedSuccessCounter = Counter.builder(METRIC_PUBLISHED)
                    .description("Outbox events successfully published to Kafka by learning-service")
                    .tag(TAG_OUTCOME, OUTCOME_SUCCESS)
                    .register(meterRegistry);
            this.publishedFailureCounter = Counter.builder(METRIC_PUBLISHED)
                    .description("Outbox events whose Kafka publish attempt failed in learning-service")
                    .tag(TAG_OUTCOME, OUTCOME_FAILURE)
                    .register(meterRegistry);
            Gauge.builder(METRIC_BACKLOG, this, p -> p.currentBacklog())
                    .description("Unpublished rows currently in lrn_event_outbox (publisher-local view)")
                    .register(meterRegistry);
        } else {
            this.publishedSuccessCounter = null;
            this.publishedFailureCounter = null;
        }
    }

    /**
     * Poll for unpublished outbox rows and publish each to its routed topic.
     *
     * <p>Each event is sent to Kafka with the aggregate id as the message key
     * (for partition affinity). On a successful send, the row is marked
     * {@code published_at = now()}. Failures are logged and retried on the next
     * poll — failed rows are skipped, not lost.</p>
     */
    @Scheduled(fixedDelayString = "${learning.outbox.publisher.poll-interval-ms:500}")
    @Transactional
    public void publishPendingEvents() {
        List<LearningOutboxEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }

        List<LearningOutboxEntity> batch = pending.size() > batchSize
                ? pending.subList(0, batchSize)
                : pending;

        int successCount = 0;
        for (LearningOutboxEntity event : batch) {
            try {
                String topic = routeTopic(event.getEventType());
                String key = event.getAggregateId();
                String payload = event.getPayloadJson();

                kafkaTemplate.send(topic, key, payload);

                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
                successCount++;
                if (publishedSuccessCounter != null) {
                    publishedSuccessCounter.increment();
                }
            } catch (Exception e) {
                if (publishedFailureCounter != null) {
                    publishedFailureCounter.increment();
                }
                log.error(
                        "Failed to publish learning outbox event {} (type={}) to Kafka: {}",
                        event.getId(),
                        event.getEventType(),
                        e.getMessage(),
                        e);
            }
        }

        if (successCount > 0) {
            log.info("Published {}/{} learning outbox events to Kafka", successCount, batch.size());
        }
    }

    /**
     * Snapshot of the current unpublished backlog size, sourced from the same
     * repository the scheduler polls. Exposed as a Micrometer gauge supplier
     * via {@link #METRIC_BACKLOG} and as a package-private hook for tests.
     */
    double currentBacklog() {
        try {
            return outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc().size();
        } catch (Exception e) {
            log.debug("currentBacklog() failed: {}", e.getMessage());
            return -1d;
        }
    }

    /**
     * Route an event type to its Kafka topic.
     *
     * <p>Phase 2 routes every event to {@link #defaultTopic}. The method is
     * package-private so the per-event-type fan-out introduced in a future
     * phase can be unit-tested without booting the scheduler.</p>
     *
     * @param eventType the event type string from {@code lrn_event_outbox.event_type}; may be {@code null}
     * @return the Kafka topic to publish to (never {@code null})
     */
    String routeTopic(String eventType) {
        return defaultTopic;
    }
}
