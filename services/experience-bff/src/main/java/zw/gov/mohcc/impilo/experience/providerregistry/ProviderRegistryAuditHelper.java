package zw.gov.mohcc.impilo.experience.providerregistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emits governance audit for provider-registry mutations (certificates, licences, compliance,
 * lifecycle, qualifications) to the Tshepo audit chain. Modeled on VashandiAuditHelper —
 * audit failures are non-blocking, never failing the operation the registrar already completed.
 */
@Component
public class ProviderRegistryAuditHelper {

    private static final Logger log = LoggerFactory.getLogger(ProviderRegistryAuditHelper.class);

    private final TshepoAuditServiceClient auditClient;

    public ProviderRegistryAuditHelper(TshepoAuditServiceClient auditClient) {
        this.auditClient = auditClient;
    }

    public void emit(String eventType, String actorId, String tenantId, String correlationId,
                     String purposeOfUse, String resourceType, String resourceId,
                     String outcome, Map<String, Object> detail) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            if (tenantId != null && !tenantId.isBlank()) {
                try { event.put("tenantId", java.util.UUID.fromString(tenantId.trim())); } catch (IllegalArgumentException ignored) { }
            }
            event.put("eventType", eventType);
            event.put("actorId", actorId != null && !actorId.isBlank() ? actorId : "SYSTEM");
            event.put("actorType", "PROVIDER");
            event.put("subjectRef", resourceType + ":" + resourceId);
            event.put("resourceType", resourceType);
            event.put("resourceId", resourceId != null && resourceId.length() > 255 ? resourceId.substring(0, 255) : resourceId);
            event.put("action", eventType);
            event.put("outcome", outcome != null && !outcome.isBlank() ? outcome : "SUCCESS");
            event.put("purposeOfUse", purposeOfUse != null && !purposeOfUse.isBlank() ? purposeOfUse : "REGISTRY_GOVERNANCE");
            event.put("correlationId", correlationId);
            if (detail != null && !detail.isEmpty()) {
                event.put("detail", detail);
            }
            auditClient.ingestAuditEvent(event);
        } catch (Exception e) {
            log.warn("Provider-registry audit emit failed (non-blocking) event={}: {}", eventType, e.getMessage());
        }
    }
}
