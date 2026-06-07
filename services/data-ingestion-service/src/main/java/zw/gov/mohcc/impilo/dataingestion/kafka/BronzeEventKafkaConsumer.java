package zw.gov.mohcc.impilo.dataingestion.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.dataingestion.api.dto.IngestEventRequest;
import zw.gov.mohcc.impilo.dataingestion.core.IngestService;
import zw.gov.mohcc.impilo.dataingestion.core.IngestService.ValidationResult;
import zw.gov.mohcc.impilo.dataingestion.domain.DeadLetterEventEntity;
import zw.gov.mohcc.impilo.dataingestion.repository.DeadLetterEventRepository;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka consumer that ingests v1.1 EventEnvelope messages from configured topic patterns
 * and writes them to the Bronze append-only store.
 * Poison messages are routed to the dead-letter table.
 */
@Component
public class BronzeEventKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(BronzeEventKafkaConsumer.class);

    /** Illustrative AsyncAPI channel; production uses {@code ingestion.kafka.topic-pattern}. */
    static final String CONTRACT_REFERENCE_TOPIC = "impilo.example.topic";

    private final IngestService ingestService;
    private final DeadLetterEventRepository deadLetterRepository;
    private final ObjectMapper objectMapper;
    private final Counter ingestedCounter;
    private final Counter deadLetterCounter;
    private final Counter duplicateCounter;
    private final AtomicLong lastConsumedOffset = new AtomicLong(-1);
    private final AtomicLong messagesConsumed = new AtomicLong(0);

    public BronzeEventKafkaConsumer(IngestService ingestService,
                                     DeadLetterEventRepository deadLetterRepository,
                                     ObjectMapper objectMapper,
                                     MeterRegistry meterRegistry) {
        this.ingestService = ingestService;
        this.deadLetterRepository = deadLetterRepository;
        this.objectMapper = objectMapper;
        this.ingestedCounter = Counter.builder("din.kafka.events.ingested")
                .description("Events successfully ingested from Kafka")
                .register(meterRegistry);
        this.deadLetterCounter = Counter.builder("din.kafka.events.dead_letter")
                .description("Events sent to dead letter")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("din.kafka.events.duplicate")
                .description("Duplicate events detected")
                .register(meterRegistry);
    }

    @KafkaListener(
            topicPattern = "${ingestion.kafka.topic-pattern:impilo\\..*}",
            groupId = "${ingestion.kafka.group-id:data-ingestion-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String topic = record.topic();
        int partition = record.partition();
        long offset = record.offset();
        String key = record.key();
        String value = record.value();

        log.debug("Kafka message received [topic={}, partition={}, offset={}]", topic, partition, offset);
        messagesConsumed.incrementAndGet();
        lastConsumedOffset.set(offset);

        try {
            IngestEventRequest request = objectMapper.readValue(value, IngestEventRequest.class);

            ValidationResult validation = ingestService.validate(request);
            if (validation != null) {
                log.warn("Validation failed for Kafka message [topic={}, offset={}]: {} - {}",
                        topic, offset, validation.code(), validation.message());
                sendToDeadLetter(topic, partition, offset, key, value,
                        validation.code() + ": " + validation.message(), "ValidationFailure");
                deadLetterCounter.increment();
                ack.acknowledge();
                return;
            }

            // Extract tenant/pod from the envelope itself (Kafka messages are self-contained)
            UUID tenantId;
            try {
                tenantId = UUID.fromString(request.tenantId());
            } catch (Exception e) {
                tenantId = new UUID(0, 0);
            }
            String podId = request.podId() != null ? request.podId() : "unknown";
            String correlationId = request.correlationId() != null ? request.correlationId() : UUID.randomUUID().toString();
            String idempotencyKey = request.idempotencyKey() != null
                    ? request.idempotencyKey()
                    : "kafka-" + topic + "-" + partition + "-" + offset;

            var response = ingestService.ingestEventFromKafka(
                    request, value, tenantId, podId,
                    UUID.randomUUID().toString(), correlationId,
                    idempotencyKey, topic);

            if ("REPLAY".equals(response.dedupe())) {
                duplicateCounter.increment();
                log.debug("Duplicate event detected from Kafka [eventId={}]", request.eventId());
            } else {
                ingestedCounter.increment();
                log.info("Ingested from Kafka [topic={}, eventId={}, receiptId={}]",
                        topic, request.eventId(), response.receiptId());
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize Kafka message [topic={}, offset={}]: {}",
                    topic, offset, e.getMessage());
            sendToDeadLetter(topic, partition, offset, key, value,
                    "Deserialization failed: " + e.getMessage(), e.getClass().getName());
            deadLetterCounter.increment();
        } catch (Exception e) {
            log.error("Unexpected error processing Kafka message [topic={}, offset={}]",
                    topic, offset, e);
            sendToDeadLetter(topic, partition, offset, key, value,
                    "Processing failed: " + e.getMessage(), e.getClass().getName());
            deadLetterCounter.increment();
        }

        ack.acknowledge();
    }

    private void sendToDeadLetter(String topic, int partition, long offset,
                                    String key, String value,
                                    String errorMessage, String errorClass) {
        try {
            DeadLetterEventEntity entity = new DeadLetterEventEntity(
                    topic, partition, offset, key, value, errorMessage, errorClass);
            deadLetterRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to save to dead letter table [topic={}, offset={}]: {}",
                    topic, offset, e.getMessage());
        }
    }

    public long getMessagesConsumed() { return messagesConsumed.get(); }
    public long getLastConsumedOffset() { return lastConsumedOffset.get(); }
}
