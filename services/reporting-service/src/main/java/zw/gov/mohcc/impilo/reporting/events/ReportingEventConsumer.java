package zw.gov.mohcc.impilo.reporting.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes analytics-channel feeds for report materialization and surveillance dashboards.
 */
@Component
public class ReportingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReportingEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public ReportingEventConsumer(ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "#{'${reporting.kafka.topics.aggregate:analytics.reporting.aggregate}'.split(',')}",
            groupId = "reporting-service")
    public void consumeAggregate(String message,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        meterRegistry.counter("impilo.eventing.consumer.messages.total",
                "service", "reporting-service", "topic", topic).increment();
        logSummary(topic, message);
    }

    @KafkaListener(
            topics = "#{'${reporting.kafka.topics.surveillance-event:analytics.surveillance.event}'.split(',')}",
            groupId = "reporting-service")
    public void consumeSurveillanceEvent(String message,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        meterRegistry.counter("impilo.eventing.consumer.messages.total",
                "service", "reporting-service", "topic", topic).increment();
        logSummary(topic, message);
    }

    private void logSummary(String topic, String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.path("event_type").asText(null);
            if (eventType == null) {
                eventType = root.path("eventType").asText(null);
            }
            log.info("{} consumed eventType={} keys={}", topic, eventType, root.size());
        } catch (JsonProcessingException e) {
            meterRegistry.counter("impilo.eventing.consumer.errors.total",
                    "service", "reporting-service", "topic", topic).increment();
            log.info("{} consumed non-JSON payload (len={})", topic, message != null ? message.length() : 0);
        }
    }
}
