package zw.gov.mohcc.impilo.surv.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.surv.core.IngestService;

import java.util.Optional;
import java.util.UUID;

/**
 * Consumes selected pharmacovigilance events for signal / cluster detection. Surveillance treats
 * patient-safety events as a SIGNAL feed only — it does NOT own the regulatory safety case; the
 * system of record stays in patient-safety-service. Serious ADR/AEFI reports and cluster candidates
 * are ingested here so existing signal logic can correlate them with other public-health streams.
 */
@Component
public class PatientSafetySignalConsumer {

    private static final Logger log = LoggerFactory.getLogger(PatientSafetySignalConsumer.class);
    private static final UUID FALLBACK_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final IngestService ingestService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public PatientSafetySignalConsumer(IngestService ingestService,
                                       ObjectMapper objectMapper,
                                       MeterRegistry meterRegistry) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "#{'${surv.kafka.topics.patient-safety:impilo.patient_safety.report.serious.v1,impilo.patient_safety.report.serious,patientsafety.reports}'.split(',')}",
            groupId = "surveillance-service")
    public void consumePatientSafetySignal(String message,
                                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        meterRegistry.counter("impilo.eventing.consumer.messages.total",
                "service", "surveillance-service", "topic", topic).increment();
        try {
            JsonNode root = objectMapper.readTree(message);
            UUID tenantId = parseUuid(root, "tenantId", "tenant_id").orElse(FALLBACK_TENANT);
            UUID facilityId = parseUuid(root, "facilityId", "facility_id").orElse(null);
            UUID correlationId = parseUuid(root, "correlationId", "correlation_id").orElse(UUID.randomUUID());

            ingestService.ingest(tenantId, "kafka:patient-safety", correlationId,
                    topic, message, facilityId,
                    "kafka:" + topic + ":" + correlationId);

            log.info("Surveillance ingest of patient-safety signal topic={} tenant={}", topic, tenantId);
        } catch (Exception e) {
            meterRegistry.counter("impilo.eventing.consumer.errors.total",
                    "service", "surveillance-service", "topic", topic).increment();
            log.warn("Failed to ingest patient-safety signal from topic {}: {}", topic, e.getMessage());
        }
    }

    private static Optional<UUID> parseUuid(JsonNode root, String... fields) {
        if (root == null) {
            return Optional.empty();
        }
        for (String field : fields) {
            JsonNode node = root.get(field);
            if (node != null && node.isTextual()) {
                try {
                    return Optional.of(UUID.fromString(node.asText()));
                } catch (IllegalArgumentException ignored) {
                    // try next field name
                }
            }
        }
        return Optional.empty();
    }
}
