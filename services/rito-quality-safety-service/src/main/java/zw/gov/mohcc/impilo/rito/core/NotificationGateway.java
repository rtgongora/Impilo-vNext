package zw.gov.mohcc.impilo.rito.core;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.rito.events.RitoEventEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Decoupled notification + learning request gateway. Rather than calling
 * notification-service / fundo synchronously (and lying about delivery), Rito emits
 * intent events through the outbox; the owning services consume them. This keeps the
 * "send engine" boundary intact (see SoR boundary doc §3) and the UI honest — Rito
 * records that a notification/learning recommendation was <em>requested</em>, not that it
 * was delivered.
 */
@Component
public class NotificationGateway {

    private final RitoEventEmitter emitter;

    public NotificationGateway(RitoEventEmitter emitter) {
        this.emitter = emitter;
    }

    /** Request a notification be sent for a case (consumed by notification-service). */
    public void requestNotification(UUID tenantId, UUID caseId, String templateKey,
                                    String audienceRole, Map<String, Object> context) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("caseId", caseId != null ? caseId.toString() : null);
        payload.put("templateKey", templateKey);
        payload.put("audienceRole", audienceRole);
        payload.put("context", context != null ? context : Map.of());
        emitter.emit("CASE", caseId != null ? caseId.toString() : UUID.randomUUID().toString(),
                "rito.notification.requested", "CASE",
                caseId != null ? caseId.toString() : null, payload, tenantId);
    }

    /**
     * Recommend a learning intervention to close a quality gap (consumed by Fundo, which
     * returns completion to Rito). Rito does not own the LMS — it only recommends.
     */
    public void recommendLearning(UUID tenantId, String sourceType, String sourceRef,
                                  String competencyArea, String rationale) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sourceType", sourceType);
        payload.put("sourceRef", sourceRef);
        payload.put("competencyArea", competencyArea);
        payload.put("rationale", rationale);
        emitter.emit("CASE", sourceRef != null ? sourceRef : UUID.randomUUID().toString(),
                "rito.learning.recommended", sourceType, sourceRef, payload, tenantId);
    }
}
