package zw.gov.mohcc.impilo.oros.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.core.BloodOrderCallbackService;
import zw.gov.mohcc.impilo.oros.persistence.entity.IdempotencyEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.IdempotencyRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.UUID;

/**
 * Event-driven path for the blood-bank loop: OROS consumes MADI's Kafka events and applies them
 * via {@link BloodOrderCallbackService} — a resilient alternative to MADI's best-effort REST
 * callbacks (either path is idempotent at the result level, and this consumer is idempotent on the
 * MADI event's idempotency key, so running both is safe).
 *
 * <p>Consumed topics: {@code madi.blood.order} (submitted/crossmatch/issued),
 * {@code madi.transfusion} (transfusion outcome). A synthetic system {@link TrustContext} is
 * established from the event's tenant since there is no request context on a Kafka thread.</p>
 */
@Service
public class MadiBloodEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MadiBloodEventConsumer.class);
    private static final String SOURCE = "MADI";

    private final BloodOrderCallbackService callbackService;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public MadiBloodEventConsumer(BloodOrderCallbackService callbackService,
                                  IdempotencyRepository idempotencyRepository,
                                  ObjectMapper objectMapper) {
        this.callbackService = callbackService;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "madi.blood.order", groupId = "oros-service")
    @Transactional
    public void consumeBloodOrderEvent(String message) {
        handle(message, event -> {
            String type = text(event, "eventType");
            JsonNode payload = event.has("payload") ? event.get("payload") : event;
            String orosRef = text(payload, "orosOrderRef");
            if (orosRef == null) {
                return false; // not an OROS-linked blood order; nothing to apply
            }
            switch (type != null ? type : "") {
                case "ORDER_SUBMITTED" -> callbackService.submitted(orosRef, text(payload, "madiOrderId"));
                case "CROSSMATCH_COMPLETED" -> callbackService.crossmatchResult(
                        orosRef, text(payload, "result"), text(payload, "notes"));
                case "BLOOD_ISSUED" -> callbackService.issued(orosRef, text(payload, "unitId"));
                default -> log.debug("Ignoring madi.blood.order event type {}", type);
            }
            return true;
        });
    }

    @KafkaListener(topics = "madi.transfusion", groupId = "oros-service")
    @Transactional
    public void consumeTransfusionEvent(String message) {
        handle(message, event -> {
            JsonNode payload = event.has("payload") ? event.get("payload") : event;
            String orosRef = text(payload, "orosOrderRef");
            if (orosRef == null) {
                return false;
            }
            if ("TRANSFUSION_COMPLETED".equals(text(event, "eventType"))) {
                callbackService.transfusionOutcome(orosRef, text(payload, "outcome"), text(payload, "notes"));
                return true;
            }
            return false;
        });
    }

    /** Deserialize, establish trust context, idempotency-guard, then apply via the supplied handler. */
    private void handle(String message, java.util.function.Function<JsonNode, Boolean> applier) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = firstNonNull(text(event, "eventId"), text(event, "idempotencyKey"));
            if (eventId != null && isAlreadyProcessed(eventId)) {
                log.debug("MADI event {} already processed, skipping", eventId);
                return;
            }
            UUID tenantId = parseUuid(text(event, "tenantId"));
            TrustContextHolder.set(new TrustContext(
                    tenantId != null ? tenantId : new UUID(0L, 0L), "madi:event-consumer", "SYSTEM",
                    "TREATMENT", null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
            try {
                boolean applied = Boolean.TRUE.equals(applier.apply(event));
                if (applied && eventId != null) {
                    markProcessed(eventId);
                }
            } finally {
                TrustContextHolder.clear();
            }
        } catch (Exception e) {
            // Best-effort: a malformed/unmatched event must not stall the partition; REST callback is the fallback.
            log.warn("Failed to process MADI blood event: {}", e.getMessage());
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return idempotencyRepository.existsById(SOURCE + ":" + eventId);
    }

    private void markProcessed(String eventId) {
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setIdempotencyKey(SOURCE + ":" + eventId);
        entity.setSourceSystem(SOURCE);
        entity.setProcessedAt(java.time.OffsetDateTime.now());
        idempotencyRepository.save(entity);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node != null ? node.get(field) : null;
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static UUID parseUuid(String s) {
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
