package zw.gov.mohcc.impilo.experience.vashandi;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Vashandi BFF DTOs — contract-aligned with web/mobile clients and vashandi-workforce-service.
 */
public final class VashandiDtos {

    private VashandiDtos() {}

    public enum IntegrationStatus {
        LIVE,
        UPSTREAM_UNAVAILABLE,
        DEGRADED,
        DENIED,
        PENDING_BACKEND
    }

    public record UpstreamResult(JsonNode data, IntegrationStatus status, String message) {
        public static UpstreamResult live(JsonNode data) {
            return new UpstreamResult(data, IntegrationStatus.LIVE, null);
        }

        public static UpstreamResult unavailable(String message) {
            return new UpstreamResult(null, IntegrationStatus.UPSTREAM_UNAVAILABLE, message);
        }

        public static UpstreamResult degraded(JsonNode data, String message) {
            return new UpstreamResult(data, IntegrationStatus.DEGRADED, message);
        }
    }

    public record PolicyDecision(
            boolean allowed,
            String policyPackage,
            String policyVersion,
            String decisionId,
            List<String> warnings,
            List<String> approvalsRequired
    ) {}

    public record PrecheckRequest(
            String requestedAction,
            String sourcePage,
            String targetWorkforceProfileId,
            String targetAssignmentId,
            String targetFacilityId,
            String roleTemplate,
            String requestedScope,
            Map<String, Object> context
    ) {}

    public record ActionResponse(
            String status,
            String requestId,
            String correlationId,
            String friendlyTitle,
            String friendlyMessage,
            List<String> allowedNextActions,
            String blockedReason,
            PolicyDecision policyDecision,
            String auditEventId,
            String auditStatus,
            String auditCorrelationId,
            IntegrationStatus integrationStatus,
            String integrationMessage,
            JsonNode data
    ) {}

    public record SessionWorkforceContext(
            String vashandiWorkforceProfileId,
            String currentWorkforceStatus,
            List<Map<String, Object>> currentAssignments,
            List<Map<String, Object>> activeRosteredShifts,
            String attendanceStatus,
            String leaveAvailabilityStatus,
            List<Map<String, Object>> workforceAccessRisks,
            List<String> visibleVashandiWorkspaces,
            List<String> blockedVashandiWorkspaces,
            String vashandiFriendlyResolutionState,
            IntegrationStatus integrationStatus
    ) {}
}
