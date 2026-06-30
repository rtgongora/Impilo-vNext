package zw.gov.mohcc.impilo.rito.events;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.rito.core.QualitySignalService;

import java.util.Map;
import java.util.UUID;

/**
 * Listens on external quality / safety signal topics (PCT, TUSO, INDAWO, MADI,
 * patient-safety, support, Fundo) and lands each event as a {@code rit_quality_signal}
 * via {@link QualitySignalService}. Signals are advisory ingestion data; a reviewer
 * later triages and, where warranted, converts a signal into a governed Rito case.
 *
 * <p>Disabled under the {@code test} profile so unit tests need no Kafka broker.
 */
@Component
@Profile("!test")
public class RitoSignalConsumer {

    private static final Logger log = LoggerFactory.getLogger(RitoSignalConsumer.class);

    private final QualitySignalService qualitySignalService;
    private final ObjectMapper objectMapper;

    public RitoSignalConsumer(QualitySignalService qualitySignalService, ObjectMapper objectMapper) {
        this.qualitySignalService = qualitySignalService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                    "${rito.signals.topics.pct}",
                    "${rito.signals.topics.tuso}",
                    "${rito.signals.topics.indawo}",
                    "${rito.signals.topics.madi}",
                    "${rito.signals.topics.patient-safety}",
                    "${rito.signals.topics.support}",
                    "${rito.signals.topics.fundo}",
                    "${rito.signals.topics.inpatient}"
            },
            groupId = "rito-quality-safety-service")
    public void onSignal(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            Map<String, Object> root = objectMapper.readValue(message, new TypeReference<>() {
            });

            UUID tenantId = parseUuid(root, "tenantId", "tenant_id");
            if (tenantId == null) {
                log.warn("Skipping signal from topic={} — no parseable tenantId in payload", topic);
                return;
            }

            UUID facilityId = parseUuid(root, "facilityId", "facility_id");
            String sourceRef = str(root, "aggregateId", "id");
            String severity = str(root, "severity");

            qualitySignalService.ingest(
                    tenantId,
                    "EXTERNAL_FINDING",
                    sourceSystemFor(topic),
                    sourceRef,
                    severity != null ? severity : "LOW",
                    facilityId,
                    null,
                    "Signal from " + topic,
                    root);
        } catch (Exception e) {
            log.error("Failed to ingest Rito signal from topic={}: {}", topic, e.getMessage(), e);
        }
    }

    private static String sourceSystemFor(String topic) {
        if (topic == null) {
            return "RULES";
        }
        String t = topic.toLowerCase();
        if (t.contains("pct")) {
            return "PCT";
        }
        if (t.contains("tuso")) {
            return "TUSO";
        }
        if (t.contains("indawo")) {
            return "INDAWO";
        }
        if (t.contains("madi")) {
            return "MADI";
        }
        if (t.contains("patientsafety") || t.contains("patient-safety") || t.contains("patient_safety")) {
            return "PATIENT_SAFETY";
        }
        if (t.contains("support")) {
            return "SUPPORT";
        }
        if (t.contains("learning") || t.contains("fundo")) {
            return "FUNDO";
        }
        if (t.contains("inpatient")) {
            return "INPATIENT";
        }
        return "RULES";
    }

    private static UUID parseUuid(Map<String, Object> root, String... keys) {
        for (String key : keys) {
            Object v = root.get(key);
            if (v != null && !v.toString().isBlank()) {
                try {
                    return UUID.fromString(v.toString());
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String str(Map<String, Object> root, String... keys) {
        for (String key : keys) {
            Object v = root.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }
}
