package zw.gov.mohcc.impilo.experience.vashandi;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/**
 * Builds {@link VashandiDtos.ActionResponse} with consistent audit and integration envelopes.
 */
public final class VashandiResponses {

    private VashandiResponses() {}

    public record AuditEnvelope(String auditEventId, String auditCorrelationId, String auditStatus) {}

    public static VashandiDtos.ActionResponse of(
            String status,
            String requestId,
            String correlationId,
            String friendlyTitle,
            String friendlyMessage,
            List<String> allowedNextActions,
            String blockedReason,
            VashandiDtos.PolicyDecision policyDecision,
            AuditEnvelope audit,
            VashandiDtos.IntegrationStatus integrationStatus,
            String integrationMessage,
            JsonNode data) {
        return new VashandiDtos.ActionResponse(
                status,
                requestId,
                correlationId,
                friendlyTitle,
                friendlyMessage,
                allowedNextActions,
                blockedReason,
                policyDecision,
                audit != null ? audit.auditEventId() : null,
                audit != null ? audit.auditStatus() : null,
                audit != null ? audit.auditCorrelationId() : null,
                integrationStatus,
                integrationMessage,
                data
        );
    }

    public static VashandiDtos.ActionResponse denied(
            String message,
            VashandiDtos.PolicyDecision decision,
            String requestId,
            String correlationId) {
        return of(
                "denied",
                requestId,
                correlationId,
                "Action not permitted",
                message,
                List.of("View My Scope", "Contact Support"),
                message,
                decision,
                null,
                VashandiDtos.IntegrationStatus.DENIED,
                message,
                null
        );
    }

    public static VashandiDtos.ActionResponse upstreamUnavailable(
            String message,
            String requestId,
            String correlationId) {
        return of(
                "upstream_unavailable",
                requestId,
                correlationId,
                "Vashandi temporarily unavailable",
                message != null ? message : "Operational workforce service is unavailable.",
                List.of("Retry", "Contact Support"),
                "upstream_unavailable",
                null,
                auditFailedNonBlocking(correlationId),
                VashandiDtos.IntegrationStatus.UPSTREAM_UNAVAILABLE,
                message,
                null
        );
    }

    public static AuditEnvelope auditIngested(String correlationId) {
        return new AuditEnvelope(UUID.randomUUID().toString(), correlationId, "ingested");
    }

    public static AuditEnvelope auditFailedNonBlocking(String correlationId) {
        return new AuditEnvelope(null, correlationId, "failed_non_blocking");
    }

    public static AuditEnvelope auditQueued(String correlationId) {
        return new AuditEnvelope(null, correlationId, "queued");
    }
}
