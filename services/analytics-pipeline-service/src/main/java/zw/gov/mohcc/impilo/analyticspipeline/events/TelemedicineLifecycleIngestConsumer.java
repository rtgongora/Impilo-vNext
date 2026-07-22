package zw.gov.mohcc.impilo.analyticspipeline.events;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.analyticspipeline.core.TelemedicineAnalyticsService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TM-B17 (G4): the §24 acceptance is "metrics from events only — no DB scraping". This consumer
 * makes the analytics store event-driven: every versioned teleconsult lifecycle event on
 * {@code clinical.teleconsult.lifecycle} is ingested directly (tenant from the envelope, full
 * envelope kept as the payload so aggregates can derive timings/outcomes) — no BFF push required.
 * The legacy BFF HTTP push remains for transition compatibility; ingest is idempotent enough for
 * the counting/aggregate use (at-least-once tolerated, no clinical decisions ride on it).
 */
@Component
public class TelemedicineLifecycleIngestConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemedicineLifecycleIngestConsumer.class);

    private final TelemedicineAnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    public TelemedicineLifecycleIngestConsumer(TelemedicineAnalyticsService analyticsService,
                                               ObjectMapper objectMapper) {
        this.analyticsService = analyticsService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"${analytics.telemedicine.lifecycle-topic:clinical.teleconsult.lifecycle}"},
            groupId = "analytics-pipeline-service")
    public void onLifecycleEvent(String message) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<>() { });
            String eventType = String.valueOf(envelope.getOrDefault("eventType", ""));
            if (!eventType.startsWith("telemedicine.")) {
                return;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", eventType);
            Object tenantId = envelope.get("tenantId");
            if (tenantId != null) {
                event.put("tenantId", String.valueOf(tenantId));
            }
            event.put("aggregateId", envelope.get("aggregateId"));
            event.put("occurredAt", envelope.get("occurredAt"));
            event.put("payload", envelope.get("payload"));
            analyticsService.ingestEvent(event);
        } catch (Exception e) {
            log.warn("Telemedicine lifecycle ingest skipped a message: {}", e.getMessage());
        }
    }
}
