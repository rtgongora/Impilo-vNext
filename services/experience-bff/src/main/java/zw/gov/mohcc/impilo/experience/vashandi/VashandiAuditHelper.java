package zw.gov.mohcc.impilo.experience.vashandi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tshepo audit ingest for governed Vashandi BFF routes.
 */
@Component
public class VashandiAuditHelper {

    private static final Logger log = LoggerFactory.getLogger(VashandiAuditHelper.class);

    private final TshepoAuditServiceClient auditClient;

    public VashandiAuditHelper(TshepoAuditServiceClient auditClient) {
        this.auditClient = auditClient;
    }

    public VashandiResponses.AuditEnvelope emit(
            String eventType,
            String actorId,
            String tenantId,
            String correlationId,
            String purposeOfUse,
            String resourceType,
            String resourceId,
            String outcome,
            Map<String, Object> detail) {
        String auditCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", eventType);
        event.put("actorId", actorId != null ? actorId : "experience-bff");
        event.put("actorType", "SERVICE");
        event.put("tenantId", tenantId);
        event.put("resourceType", resourceType);
        event.put("resourceId", resourceId);
        event.put("action", eventType);
        event.put("outcome", outcome != null ? outcome : "SUCCESS");
        event.put("purposeOfUse", purposeOfUse != null && !purposeOfUse.isBlank() ? purposeOfUse : "WORKFORCE_OPERATIONS");
        event.put("occurredAt", OffsetDateTime.now().toString());
        event.put("correlationId", auditCorrelationId);
        if (detail != null && !detail.isEmpty()) {
            event.put("detail", detail);
        }
        try {
            auditClient.ingestAuditEvent(event);
            return VashandiResponses.auditIngested(auditCorrelationId);
        } catch (Exception e) {
            log.warn("Vashandi audit ingest failed for {}: {}", eventType, e.getMessage());
            return VashandiResponses.auditFailedNonBlocking(auditCorrelationId);
        }
    }
}
