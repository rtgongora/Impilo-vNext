package zw.gov.mohcc.impilo.pct.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.pct.core.clinical.NewbornEpisodeService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Opens the newborn's clinical episode when a delivery is recorded in theatre.
 *
 * <p>inpatient-service records a delivery as an intra-operative event on the mother's
 * theatre episode and emits {@code theatre.obstetric.delivery} carrying the baby's CPID.
 * That event is the moment the child becomes a patient in their own right, so PCT reacts
 * to it by opening the baby's birth record, journey and encounter. Without this, the first
 * clinical write about the baby — an APGAR score, a newborn examination — has no encounter
 * of the child's own to attach to and is refused by the care-relationship guard.</p>
 *
 * <p>Episode creation is idempotent on the baby's CPID, so a redelivered Kafka message, a
 * partition rebalance, or a delivery also captured on a maternity form all converge on one
 * record rather than splitting the child's history.</p>
 */
@Component
@Profile("!test")
public class TheatreObstetricConsumer {

    private static final Logger log = LoggerFactory.getLogger(TheatreObstetricConsumer.class);

    private static final String DELIVERY_EVENT = "theatre.obstetric.delivery";
    private static final String HANDOVER_EVENT = "theatre.obstetric.neonatal-handover";

    private final ObjectMapper objectMapper;
    private final NewbornEpisodeService newbornEpisodeService;

    public TheatreObstetricConsumer(ObjectMapper objectMapper, NewbornEpisodeService newbornEpisodeService) {
        this.objectMapper = objectMapper;
        this.newbornEpisodeService = newbornEpisodeService;
    }

    @KafkaListener(topics = {"inpatient.events"}, groupId = "pct-service-newborn-episode")
    public void onTheatreEvent(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = extractPayload(root);
            if (payload == null || !payload.isObject()) {
                return;
            }

            String eventType = firstNonBlank(text(root, "event_type"), text(root, "eventType"));
            if (!isObstetricEvent(eventType, payload)) {
                return;
            }

            String babyCpid = firstNonBlank(text(payload, "baby_cpid"), text(payload, "babyCpid"));
            if (babyCpid == null) {
                log.warn("PCT: theatre obstetric event carried no baby CPID, skipping");
                return;
            }

            Map<String, Object> body = toMap(payload);
            body.put("subject_cpid", babyCpid);
            body.put("delivery_source", "THEATRE");
            body.put("delivery_source_ref", firstNonBlank(text(payload, "episode_id"), text(payload, "episodeId")));

            // Kafka delivers no request context, so the consumer runs as the service itself in
            // the tenant the event was raised in. The subject-relationship guard is not applied
            // here for the same reason it is waived in an emergency: a newborn cannot be left
            // without a clinical record because no clinician session happens to be open.
            UUID tenantId = parseUuid(firstNonBlank(text(root, "tenant_id"), text(payload, "tenant_id")));
            if (tenantId == null) {
                log.warn("PCT: theatre obstetric event for baby carried no tenant, skipping");
                return;
            }
            withServiceContext(tenantId, () -> newbornEpisodeService.ensureNewbornEpisode(body));

        } catch (JsonProcessingException e) {
            log.error("PCT: failed to parse theatre obstetric event: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("PCT: error opening newborn episode from theatre event: {}", e.getMessage(), e);
        }
    }

    /**
     * The v1.1 envelope names the event; the legacy emission is the bare payload. For legacy
     * messages the delivery is identified by its distinguishing fields — a baby CPID together
     * with a birth outcome — so that a neonatal handover, which shares the baby CPID but
     * carries a destination instead, is not mistaken for a delivery.
     */
    private static boolean isObstetricEvent(String eventType, JsonNode payload) {
        if (eventType != null) {
            return DELIVERY_EVENT.equals(eventType) || HANDOVER_EVENT.equals(eventType);
        }
        boolean hasBaby = payload.hasNonNull("baby_cpid") || payload.hasNonNull("babyCpid");
        return hasBaby && (payload.hasNonNull("outcome") || payload.hasNonNull("destination"));
    }

    private void withServiceContext(UUID tenantId, Runnable action) {
        TrustContext previous = TrustContextHolder.get();
        try {
            TrustContextHolder.set(new TrustContext(
                    tenantId, "pct-service", "SERVICE", "TREATMENT", null,
                    UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
            action.run();
        } finally {
            if (previous != null) {
                TrustContextHolder.set(previous);
            } else {
                TrustContextHolder.clear();
            }
        }
    }

    private Map<String, Object> toMap(JsonNode payload) {
        Map<String, Object> body = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = payload.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (value == null || value.isNull()) {
                continue;
            }
            body.put(field.getKey(), value.isValueNode() ? value.asText() : value);
        }
        return body;
    }

    private JsonNode extractPayload(JsonNode root) {
        if (!root.has("payload")) {
            return root;
        }
        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull()) {
            return null;
        }
        if (payload.isTextual()) {
            try {
                return objectMapper.readTree(payload.asText());
            } catch (JsonProcessingException e) {
                return null;
            }
        }
        return payload;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText(null);
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
