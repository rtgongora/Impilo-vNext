package zw.gov.mohcc.impilo.iotingestion.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.iotingestion.api.dto.IngestTelemetryRequest;
import zw.gov.mohcc.impilo.iotingestion.core.TelemetryIngestionService;
import zw.gov.mohcc.impilo.iotingestion.domain.TelemetryDlqEntity;
import zw.gov.mohcc.impilo.iotingestion.repository.TelemetryDlqRepository;

import java.util.UUID;

@Component
public class TelemetryKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryKafkaConsumer.class);

    private final TelemetryIngestionService ingestionService;
    private final TelemetryDlqRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public TelemetryKafkaConsumer(TelemetryIngestionService ingestionService,
                                  TelemetryDlqRepository dlqRepository,
                                  ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${impilo.telemetry.kafka.topic:impilo.iot.telemetry.device.raw}",
            groupId = "${impilo.telemetry.kafka.group-id:iot-ingestion-telemetry-cg}",
            autoStartup = "${impilo.telemetry.kafka.enabled:false}")
    public void onTelemetryMessage(String message) {
        log.debug("Received Kafka telemetry message");

        try {
            IngestTelemetryRequest request = objectMapper.readValue(message, IngestTelemetryRequest.class);

            // Extract tenant from message or use default — in production,
            // tenant would be in the Kafka header or message envelope
            UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");

            ingestionService.ingestReading(tenantId, "national-spine", null, request, "KAFKA");

        } catch (Exception e) {
            log.error("Failed to process Kafka telemetry message: {}", e.getMessage());
            TelemetryDlqEntity dlq = new TelemetryDlqEntity(
                    null, null, message,
                    "KAFKA_DESERIALIZATION_FAILED",
                    e.getMessage(),
                    "KAFKA", null);
            dlqRepository.save(dlq);
        }
    }
}
