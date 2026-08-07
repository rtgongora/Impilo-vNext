package zw.gov.mohcc.impilo.sharedkernel.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Framework-agnostic outbox publisher base class for v1.1 EventEnvelope publishing.
 *
 * <p>Provides the core polling/publishing loop that services wire into their
 * scheduling framework (Spring @Scheduled, Quartz, etc.). Handles:</p>
 * <ul>
 *   <li>Legacy vs v1.1 vs dual emit routing via {@link DualEmitPolicy}</li>
 *   <li>EventEnvelope construction from outbox rows</li>
 *   <li>Partition key derivation (from outbox row or subject_id fallback)</li>
 *   <li>Serialization to canonical JSON wire format</li>
 *   <li>Poison message handling (marks failed rows instead of blocking)</li>
 * </ul>
 *
 * <p>Subclasses implement the 4 integration points:
 * {@link #fetchUnpublished()}, {@link #sendToKafka(String, String, String)},
 * {@link #markPublished(OutboxRow, OffsetDateTime)}, {@link #markFailed(OutboxRow, String)}.</p>
 *
 * <p>Additionally, subclasses must provide:</p>
 * <ul>
 *   <li>{@link #resolveLegacyTopic(OutboxRow)} — legacy topic routing</li>
 *   <li>{@link #resolveV11Topic(OutboxRow)} — v1.1 topic routing (or use {@link EventTopicRegistry})</li>
 * </ul>
 */
public abstract class CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(CompanionOutboxPublisher.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final int MAX_RETRIES = 3;

    private final DualEmitPolicy emitPolicy;
    private final EventTopicRegistry topicRegistry;

    /**
     * @param emitPolicy the dual-emit policy to use
     * @param topicRegistry the topic registry for this service (used for default v1.1 topics)
     */
    protected CompanionOutboxPublisher(DualEmitPolicy emitPolicy, EventTopicRegistry topicRegistry) {
        this.emitPolicy = Objects.requireNonNull(emitPolicy);
        this.topicRegistry = Objects.requireNonNull(topicRegistry);
    }

    // ── Abstract integration points ──

    /** Fetch unpublished outbox rows ordered by creation time (limit recommended: 100). */
    protected abstract List<? extends OutboxRow> fetchUnpublished();

    /** Send a message to Kafka. */
    protected abstract void sendToKafka(String topic, String key, String value);

    /** Mark an outbox row as published. */
    protected abstract void markPublished(OutboxRow row, OffsetDateTime publishedAt);

    /** Mark an outbox row as failed (poison message handling). */
    protected abstract void markFailed(OutboxRow row, String errorMessage);

    /** Resolve the legacy Kafka topic for an outbox row. Return null to skip legacy emit. */
    protected abstract String resolveLegacyTopic(OutboxRow row);

    /**
     * Resolve the v1.1 Kafka topic for an outbox row.
     * Default implementation uses the EventTopicRegistry with aggregateType as domain.
     */
    protected String resolveV11Topic(OutboxRow row) {
        return topicRegistry.v11Topic(row.aggregateType());
    }

    /**
     * Additional legacy topics this row must also reach, beyond {@link #resolveLegacyTopic}.
     *
     * <p>Most services publish one legacy topic per event. Some fan the same payload out to
     * several — a canonical companion topic, a core-transaction stream — and dropping those
     * during conversion would silently unsubscribe their consumers. Overriding this preserves
     * the existing fan-out; the default is empty, so no already-converted service changes
     * behaviour.</p>
     *
     * <p>These topics receive the raw payload, exactly as the primary legacy topic does. A
     * null entry, or a duplicate of the primary topic, is skipped.</p>
     */
    protected List<String> additionalLegacyTopics(OutboxRow row) {
        return List.of();
    }

    // ── Core publishing loop ──

    /**
     * Poll for unpublished events and publish them. Call this from your scheduler.
     * @return number of successfully published events
     */
    public int publishPendingEvents() {
        List<? extends OutboxRow> events = fetchUnpublished();
        if (events == null || events.isEmpty()) {
            return 0;
        }

        int published = 0;
        for (OutboxRow row : events) {
            try {
                // Legacy emit
                if (emitPolicy.emitLegacy()) {
                    String legacyTopic = resolveLegacyTopic(row);
                    String key = row.aggregateId();
                    String payload = row.payloadJson();
                    if (legacyTopic != null) {
                        sendToKafka(legacyTopic, key, payload);
                    }
                    for (String extraTopic : additionalLegacyTopics(row)) {
                        if (extraTopic != null && !extraTopic.equals(legacyTopic)) {
                            sendToKafka(extraTopic, key, payload);
                        }
                    }
                }

                // V1.1 emit
                if (emitPolicy.emitV11()) {
                    emitV11Event(row);
                }

                markPublished(row, OffsetDateTime.now());
                published++;
            } catch (Exception e) {
                // A silently-swallowed failure here head-of-line-blocks the whole
                // drain: the row is never marked published, so every later row
                // starves. Always log so the poison row is diagnosable.
                log.warn("Outbox drain halted at row aggregateType={} eventType={} id={}: {}",
                        row.aggregateType(), row.eventType(), row.id(), e.toString(), e);
                if (row.retryCount() >= MAX_RETRIES) {
                    markFailed(row, e.getMessage());
                }
                // Stop processing to preserve ordering
                break;
            }
        }
        return published;
    }

    /**
     * Get the effective emit mode.
     */
    public EmitMode effectiveEmitMode() {
        return emitPolicy.mode();
    }

    /**
     * Get the topic registry.
     */
    public EventTopicRegistry topicRegistry() {
        return topicRegistry;
    }

    // ── V1.1 envelope construction ──

    private void emitV11Event(OutboxRow row) throws JsonProcessingException {
        String v11Topic = resolveV11Topic(row);

        Map<String, Object> payloadMap = parsePayload(row.payloadJson());

        EventEnvelope envelope = OutboxEventBuilder.forProducer(topicRegistry.serviceId())
                .aggregateType(row.aggregateType())
                .aggregateId(row.aggregateId())
                .eventType(row.eventType() != null ? row.eventType() : deriveEventType(row))
                .schemaVersion(row.schemaVersion())
                .tenantId(row.tenantId())
                .podId(row.podId())
                .correlationId(row.correlationId())
                .causationId(row.causationId())
                .idempotencyKey(row.idempotencyKey())
                .occurredAt(row.occurredAt())
                .subjectType(row.subjectType())
                .subjectId(row.subjectId() != null ? row.subjectId() : row.aggregateId())
                .partitionKey(row.partitionKey() != null ? row.partitionKey() : row.aggregateId())
                .payload(payloadMap)
                .build();

        String envelopeJson = serializeEnvelope(envelope);
        sendToKafka(v11Topic, envelope.partitionKey(), envelopeJson);
    }

    private String deriveEventType(OutboxRow row) {
        String entity = row.aggregateType() != null
                ? row.aggregateType().toLowerCase().replace("_", ".")
                : "unknown";
        String action = row.eventType() != null
                ? extractAction(row.eventType())
                : "unknown";
        return topicRegistry.eventType(entity, action);
    }

    private static String extractAction(String rawEventType) {
        if (rawEventType == null) return "unknown";
        // Handle v1.1 format: impilo.service.entity.action.v1
        if (rawEventType.contains(".v")) {
            int versionDot = rawEventType.lastIndexOf(".v");
            String withoutVersion = rawEventType.substring(0, versionDot);
            int lastDot = withoutVersion.lastIndexOf('.');
            return lastDot >= 0 ? withoutVersion.substring(lastDot + 1) : withoutVersion;
        }
        // Handle legacy: ENTITY_ACTION or entity.action
        int lastSep = Math.max(rawEventType.lastIndexOf('_'), rawEventType.lastIndexOf('.'));
        if (lastSep >= 0 && lastSep < rawEventType.length() - 1) {
            return rawEventType.substring(lastSep + 1).toLowerCase();
        }
        return rawEventType.toLowerCase();
    }

    // ── Serialization ──

    /**
     * Serialize an EventEnvelope to the canonical v1.1 JSON wire format.
     */
    public static String serializeEnvelope(EventEnvelope envelope) throws JsonProcessingException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("event_id", envelope.eventId());
        map.put("event_type", envelope.eventType());
        map.put("schema_version", envelope.schemaVersion());
        map.put("correlation_id", envelope.correlationId());
        map.put("causation_id", envelope.causationId());
        map.put("idempotency_key", envelope.idempotencyKey());
        map.put("producer", envelope.producer());
        map.put("tenant_id", envelope.tenantId());
        map.put("pod_id", envelope.podId());
        map.put("occurred_at", envelope.occurredAt().toString());
        map.put("emitted_at", envelope.emittedAt().toString());
        map.put("subject_type", envelope.subjectType());
        map.put("subject_id", envelope.subjectId());
        map.put("payload", envelope.payload());
        map.put("meta", envelope.meta());
        return MAPPER.writeValueAsString(map);
    }

    private static Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    // ── OutboxRow contract ──

    /**
     * Interface representing a row in the outbox table.
     * Services map their JPA entity to this interface.
     */
    public interface OutboxRow {
        Long id();
        String aggregateType();
        String aggregateId();
        String eventType();
        String payloadJson();
        OffsetDateTime occurredAt();
        OffsetDateTime publishedAt();

        // v1.1 context — may return null for legacy rows
        default String tenantId() { return null; }
        default String podId() { return null; }
        default String correlationId() { return null; }
        default String causationId() { return null; }
        default String idempotencyKey() { return null; }
        default int schemaVersion() { return 1; }
        default String subjectType() { return aggregateType(); }
        default String subjectId() { return aggregateId(); }
        default String partitionKey() { return aggregateId(); }
        default int retryCount() { return 0; }
    }
}
