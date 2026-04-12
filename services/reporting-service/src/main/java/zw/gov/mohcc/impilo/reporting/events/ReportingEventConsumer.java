package zw.gov.mohcc.impilo.reporting.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes analytics-channel feeds for report materialization and surveillance dashboards.
 */
@Component
public class ReportingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReportingEventConsumer.class);

    private final ObjectMapper objectMapper;

    public ReportingEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "analytics.reporting.aggregate", groupId = "reporting-service")
    public void consumeAggregate(String message) {
        logSummary("analytics.reporting.aggregate", message);
    }

    @KafkaListener(topics = "analytics.surveillance.event", groupId = "reporting-service")
    public void consumeSurveillanceEvent(String message) {
        logSummary("analytics.surveillance.event", message);
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
            log.info("{} consumed non-JSON payload (len={})", topic, message != null ? message.length() : 0);
        }
    }
}
