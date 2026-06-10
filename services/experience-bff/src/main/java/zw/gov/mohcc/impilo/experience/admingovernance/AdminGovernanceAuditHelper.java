package zw.gov.mohcc.impilo.experience.admingovernance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AdminGovernanceAuditHelper {

    private static final Logger log = LoggerFactory.getLogger(AdminGovernanceAuditHelper.class);

    private final TshepoAuditServiceClient auditClient;

    public AdminGovernanceAuditHelper(TshepoAuditServiceClient auditClient) {
        this.auditClient = auditClient;
    }

    public AdminGovernanceResponses.AuditEnvelope emit(String eventType, String actorId, String subjectRef, Map<String, Object> payload) {
        String correlationId = UUID.randomUUID().toString();
        String auditId = UUID.randomUUID().toString();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", auditId);
        event.put("eventType", eventType);
        event.put("actorId", actorId);
        event.put("subjectRef", subjectRef);
        event.put("correlationId", correlationId);
        event.put("payload", payload != null ? payload : Map.of());
        try {
            auditClient.ingestAuditEvent(event);
            return AdminGovernanceResponses.auditIngested(correlationId);
        } catch (Exception e) {
            log.warn("Audit ingest failed for {}: {}", eventType, e.getMessage());
            return AdminGovernanceResponses.auditFailedNonBlocking(correlationId);
        }
    }
}
