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
import zw.gov.mohcc.impilo.rito.core.VerifiedInteractionService;

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
    private final VerifiedInteractionService verifiedInteractionService;
    private final ObjectMapper objectMapper;

    public RitoSignalConsumer(QualitySignalService qualitySignalService,
                              VerifiedInteractionService verifiedInteractionService,
                              ObjectMapper objectMapper) {
        this.qualitySignalService = qualitySignalService;
        this.verifiedInteractionService = verifiedInteractionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Verified-interaction intake (RW2): a completed PCT encounter becomes an
     * eligible-for-feedback interaction, so a later rating referencing the same
     * encounter can be stamped verified. Distinct from the advisory quality-signal
     * lane above — this records the interaction, it does not open a case.
     */
    /**
     * TM-B17 (G1): a COMPLETED teleconsult becomes an eligible-for-feedback verified interaction,
     * exactly like a completed facility encounter — journey #29's missing Rito hook. Envelope shape
     * (pct outbox): {eventType, aggregateId=referralId, tenantId, occurredAt, payload:{referral…}}
     * on clinical.teleconsult.lifecycle; only telemedicine.session.completed(.v1) records here.
     * encounterRef = the referral id — the teleconsult case IS the verified interaction, so the
     * existing rating capture (/feedback/visit/{ref}) verifies against it unchanged.
     */
    @KafkaListener(
            topics = {"${rito.signals.topics.teleconsult-lifecycle:clinical.teleconsult.lifecycle}"},
            groupId = "rito-quality-safety-service")
    public void onTeleconsultLifecycle(String message) {
        try {
            Map<String, Object> root = objectMapper.readValue(message, new TypeReference<>() {
            });
            String eventType = String.valueOf(root.getOrDefault("eventType", ""));
            if (!eventType.startsWith("telemedicine.session.completed")) {
                return; // only completions become feedback-eligible interactions
            }
            UUID tenantId = parseUuid(root, "tenantId", "tenant_id");
            String referralId = String.valueOf(root.getOrDefault("aggregateId", "")).trim();
            if (tenantId == null || referralId.isEmpty() || "null".equals(referralId)) {
                log.warn("Skipping teleconsult-completed — missing tenantId or referral aggregateId");
                return;
            }
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            if (root.get("payload") instanceof Map<?, ?> inner) {
                for (Map.Entry<?, ?> entry : inner.entrySet()) {
                    payload.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            payload.put("encounterRef", referralId);
            if (!payload.containsKey("attendingProviderId") && payload.get("providerId") != null) {
                payload.put("attendingProviderId", payload.get("providerId"));
            }
            verifiedInteractionService.recordFromPct(tenantId, payload);
        } catch (Exception e) {
            log.error("Failed to record verified interaction from teleconsult-completed: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = {"${rito.signals.topics.pct-encounter-completed:pct.encounter.completed}"},
            groupId = "rito-quality-safety-service")
    public void onEncounterCompleted(String message) {
        try {
            Map<String, Object> root = objectMapper.readValue(message, new TypeReference<>() {
            });
            UUID tenantId = parseUuid(root, "tenantId", "tenant_id");
            if (tenantId == null) {
                log.warn("Skipping encounter-completed — no parseable tenantId");
                return;
            }
            verifiedInteractionService.recordFromPct(tenantId, root);
        } catch (Exception e) {
            log.error("Failed to record verified interaction from encounter-completed: {}", e.getMessage(), e);
        }
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
