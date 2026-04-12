package zw.gov.mohcc.impilo.surv.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.surv.core.CounterService;
import zw.gov.mohcc.impilo.surv.core.IngestService;
import zw.gov.mohcc.impilo.surv.persistence.entity.AlertEventEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.AlertEventRepository;

/**
 * Consumes clinical and analytics topics for public-health surveillance.
 */
@Component
public class SurveillanceEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SurveillanceEventConsumer.class);

    private static final UUID FALLBACK_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final IngestService ingestService;
    private final CounterService counterService;
    private final AlertEventRepository alertEventRepository;
    private final ObjectMapper objectMapper;

    public SurveillanceEventConsumer(IngestService ingestService,
                                     CounterService counterService,
                                     AlertEventRepository alertEventRepository,
                                     ObjectMapper objectMapper) {
        this.ingestService = ingestService;
        this.counterService = counterService;
        this.alertEventRepository = alertEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "clinical.pct.encounter.completed", groupId = "surveillance-service")
    public void consumeEncounterCompleted(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            UUID tenantId = parseUuid(root, "tenantId", "tenant_id").orElse(FALLBACK_TENANT);
            UUID facilityId = parseUuid(root, "facilityId", "facility_id").orElse(null);
            UUID correlationId = parseUuid(root, "correlationId", "correlation_id").orElse(UUID.randomUUID());

            if (!hasNotifiableCondition(root)) {
                log.debug("Encounter event has no notifiable markers, skipping ingest tenant={}", tenantId);
                return;
            }

            ingestService.ingest(tenantId, "kafka:surveillance", correlationId,
                    "clinical.pct.encounter.completed", message, facilityId,
                    "kafka:clinical.pct.encounter.completed:" + correlationId);

            log.info("Surveillance ingest for notifiable encounter tenant={} facility={}", tenantId, facilityId);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse clinical.pct.encounter.completed: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "clinical.pct.death.recorded", groupId = "surveillance-service")
    public void consumeDeathRecorded(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            UUID tenantId = parseUuid(root, "tenantId", "tenant_id").orElse(FALLBACK_TENANT);
            UUID facilityId = parseUuid(root, "facilityId", "facility_id").orElse(tenantId);

            counterService.incrementCounter(tenantId, facilityId, "MORTALITY", LocalDate.now());

            log.info("Recorded mortality counter increment tenant={} facility={}", tenantId, facilityId);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse clinical.pct.death.recorded: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "analytics.surveillance.alert", groupId = "surveillance-service")
    public void consumeSurveillanceAlert(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            Long alertEventId = parseLong(root, "alertEventId", "alert_event_id", "id");
            if (alertEventId != null) {
                Optional<AlertEventEntity> existing = alertEventRepository.findById(alertEventId);
                if (existing.isPresent()) {
                    log.info("Escalation tracking: acknowledged alert_event id={} tenant={}",
                            alertEventId, existing.get().getTenantId());
                } else {
                    log.warn("Escalation tracking: alert_event id={} not found locally", alertEventId);
                }
            } else {
                log.info("Surveillance alert consumed for escalation tracking (no local id in payload)");
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse analytics.surveillance.alert: {}", e.getMessage(), e);
        }
    }

    private static boolean hasNotifiableCondition(JsonNode root) {
        if (root.path("notifiable").asBoolean(false)) {
            return true;
        }
        JsonNode flags = root.path("flags");
        if (flags.isObject() && flags.path("notifiable").asBoolean(false)) {
            return true;
        }
        JsonNode diagnoses = root.path("diagnoses");
        if (diagnoses.isArray()) {
            for (JsonNode d : diagnoses) {
                String code = text(d, "code", "icd10", "snomed");
                if (code != null && (code.regionMatches(true, 0, "A00", 0, 3)
                        || code.regionMatches(true, 0, "A09", 0, 3)
                        || code.regionMatches(true, 0, "U07", 0, 3)
                        || code.toLowerCase().contains("cholera")
                        || code.toLowerCase().contains("measles"))) {
                    return true;
                }
            }
        }
        JsonNode payload = root.path("payload");
        if (payload.isObject()) {
            return hasNotifiableCondition(payload);
        }
        return false;
    }

    private static String text(JsonNode node, String... names) {
        for (String n : names) {
            JsonNode v = node.get(n);
            if (v != null && !v.isNull() && v.isTextual()) {
                return v.asText();
            }
        }
        return null;
    }

    private static Optional<UUID> parseUuid(JsonNode node, String... names) {
        for (String n : names) {
            JsonNode v = node.get(n);
            if (v != null && !v.isNull() && v.isTextual()) {
                try {
                    return Optional.of(UUID.fromString(v.asText()));
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static Long parseLong(JsonNode node, String... names) {
        for (String n : names) {
            JsonNode v = node.get(n);
            if (v != null && !v.isNull()) {
                if (v.isIntegralNumber()) {
                    return v.longValue();
                }
                if (v.isTextual()) {
                    try {
                        return Long.parseLong(v.asText());
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
}
