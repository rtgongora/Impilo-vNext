package zw.gov.mohcc.impilo.experience.admingovernance;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds {@link AdminGovernanceDtos.ActionResponse} with consistent audit envelope fields.
 */
public final class AdminGovernanceResponses {

    private AdminGovernanceResponses() {}

    public record AuditEnvelope(String auditEventId, String auditCorrelationId, String auditStatus) {}

    public static AdminGovernanceDtos.ActionResponse of(
            String status,
            String requestId,
            String subjectId,
            String assignmentId,
            String friendlyTitle,
            String friendlyMessage,
            List<String> allowedNextActions,
            String blockedReason,
            AdminGovernanceDtos.PolicyDecision policyDecision,
            AuditEnvelope audit,
            String integrationStatus,
            Map<String, Object> payload) {
        return new AdminGovernanceDtos.ActionResponse(
                status,
                requestId,
                subjectId,
                assignmentId,
                friendlyTitle,
                friendlyMessage,
                allowedNextActions,
                blockedReason,
                policyDecision,
                audit != null ? audit.auditEventId() : null,
                integrationStatus,
                payload,
                audit != null ? audit.auditStatus() : null,
                audit != null ? audit.auditCorrelationId() : null
        );
    }

    public static AdminGovernanceDtos.ActionResponse denied(String message, AdminGovernanceDtos.PolicyDecision decision) {
        return of(
                "denied",
                null,
                null,
                null,
                "Action not permitted",
                message,
                List.of("View My Scope", "Contact Support"),
                message,
                decision,
                null,
                "live",
                Map.of()
        );
    }

    public static AdminGovernanceDtos.ActionResponse pendingBackend(String message, AdminGovernanceDtos.PolicyDecision decision) {
        return of(
                "pending_backend",
                null,
                null,
                null,
                "Backend integration pending",
                message,
                List.of("Submit access request", "Contact Support"),
                "pending_backend",
                decision,
                new AuditEnvelope(null, null, "pending_backend"),
                "pending_backend",
                Map.of()
        );
    }

    public static AuditEnvelope auditIngested(String correlationId) {
        String auditId = UUID.randomUUID().toString();
        return new AuditEnvelope(auditId, correlationId, "ingested");
    }

    public static AuditEnvelope auditFailedNonBlocking(String correlationId) {
        return new AuditEnvelope(null, correlationId, "failed_non_blocking");
    }
}
