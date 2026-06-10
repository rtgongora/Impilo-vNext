package zw.gov.mohcc.impilo.experience.bootstrap;

import java.util.List;
import java.util.Map;

public final class BootstrapDtos {

    private BootstrapDtos() {}

    public record BootstrapStatusResponse(
            boolean bootstrapRequired,
            boolean bootstrapOpen,
            String bootstrapClosedAt,
            boolean activeNationalAdminExists,
            boolean recoveryMode,
            List<String> allowedBootstrapMethods,
            String friendlyMessage
    ) {}

    public record BootstrapStateRecord(
            String id,
            String tenantId,
            boolean bootstrapOpen,
            boolean bootstrapClosed,
            String bootstrapClosedAt,
            boolean recoveryMode,
            String recoveryOpenedAt,
            String recoveryClosedAt,
            String activeNationalAdminUserId,
            String bootstrapAccountId,
            String bootstrapMethod,
            String roleExpiresAt,
            String createdAt,
            String updatedAt,
            Map<String, Object> metadata
    ) {}

    public record ValidateTokenRequest(String bootstrapMethod, String bootstrapToken, String authorisationReference) {}

    public record ValidateTokenResponse(
            String status,
            String friendlyTitle,
            String friendlyMessage,
            boolean tokenValid,
            String auditStatus,
            String auditEventId
    ) {}

    public record BootstrapFirstAdminRequest(
            String bootstrapMethod,
            String bootstrapToken,
            String authorisationReference,
            Map<String, Object> sovereignOrganisation,
            Map<String, Object> nominatedPerson,
            List<String> requestedRoles,
            String approvalReference,
            Boolean mfaRequired,
            String environment
    ) {}

    public record BootstrapFirstAdminResponse(
            String status,
            String bootstrapAccountId,
            String organisationId,
            String invitationId,
            String friendlyTitle,
            String friendlyMessage,
            boolean bootstrapClosed,
            String roleExpiry,
            String auditStatus,
            String auditEventId,
            BootstrapPolicyDecision policyDecision
    ) {}

    public record BootstrapActivateRequest(
            String bootstrapAccountId,
            String password,
            Boolean mfaConfigured
    ) {}

    public record BootstrapRecoveryRequest(String ceremonyReference, String notes) {}

    public record BootstrapPolicyDecision(
            boolean allowed,
            String decisionId,
            List<String> warnings,
            List<String> blockedReasons
    ) {}
}
