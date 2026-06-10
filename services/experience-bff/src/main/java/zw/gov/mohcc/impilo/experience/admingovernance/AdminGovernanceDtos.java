package zw.gov.mohcc.impilo.experience.admingovernance;

import java.util.List;
import java.util.Map;

/**
 * Admin & Governance BFF DTOs — contract-aligned with web/mobile clients.
 */
public final class AdminGovernanceDtos {

    private AdminGovernanceDtos() {}

    public record PolicyDecision(
            boolean allowed,
            String policyPackage,
            String policyVersion,
            String decisionId,
            List<String> warnings,
            List<String> approvalsRequired
    ) {}

    public record ActionResponse(
            String status,
            String requestId,
            String subjectId,
            String assignmentId,
            String friendlyTitle,
            String friendlyMessage,
            List<String> allowedNextActions,
            String blockedReason,
            PolicyDecision policyDecision,
            String auditEventId,
            String integrationStatus,
            Map<String, Object> payload,
            String auditStatus,
            String auditCorrelationId
    ) {}

    public record PrecheckRequest(
            String requestedAction,
            String sourcePage,
            String targetOrganisationId,
            String targetSubjectId,
            String roleTemplate,
            String requestedScope,
            String requestedEnvironment,
            String requestedDataScope,
            Integer requestedDurationDays,
            Boolean crossBorderFlag,
            String riskLevel,
            Map<String, Object> context
    ) {}

    public record AccessRequestRecord(
            String id,
            String requestType,
            String status,
            String requesterId,
            String requesterName,
            String organisationId,
            String organisationName,
            String targetSubjectId,
            String requestedRole,
            String requestedScope,
            String requestedEnvironment,
            String requestedDataScope,
            String riskLevel,
            String policyPrecheckResult,
            List<String> approvalsRequired,
            List<Map<String, Object>> auditTrail,
            String createdAt,
            String updatedAt,
            Map<String, Object> payload,
            String sourceOfRecordStatus,
            String fallbackRequestId,
            String durableRequestId,
            String downstreamCorrelationId,
            Boolean reconciliationRequired,
            String lastSyncAttemptAt,
            String lastSyncStatus
    ) {}

    public record OnboardingSubmission(
            String flowId,
            String organisationId,
            String targetHealthId,
            String targetProviderWorkerId,
            String roleTemplate,
            String requestedScope,
            String requestedEnvironment,
            String requestedDataScope,
            Integer requestedDurationDays,
            Boolean crossBorderFlag,
            String purpose,
            String country,
            Map<String, Object> profile
    ) {}

    public record FacilityStaffAssignmentRequest(
            String facilityId,
            String providerWorkerId,
            String departmentId,
            String workspaceId,
            String roleTemplate,
            String startDate,
            String endDate,
            boolean assumptionOfDutyRequired
    ) {}

    public record AccessRequestDecisionRequest(
            String decision,
            String notes,
            String escalationTarget
    ) {}

    public record OrganisationUpsertRequest(
            String organisationCode,
            String organisationType,
            String legalName,
            String tradingName,
            String country,
            String registrationNumber,
            String regulatorRegistrationNumber,
            String parentOrganisationId,
            String ownershipType,
            String publicPrivateClassification,
            List<String> operatingJurisdictions,
            Map<String, Object> provenanceMetadata
    ) {}

    public record OrganisationPatchRequest(
            String legalName,
            String tradingName,
            String licenceStatus,
            String contractStatus,
            String mouStatus,
            String dataSharingAgreementStatus,
            String regulatoryApprovalStatus,
            String riskRating,
            String trustTier,
            String verificationStatus,
            String lifecycleStatus,
            Map<String, Object> provenanceMetadata
    ) {}

    public record OrganisationUserRequest(
            String userId,
            String roleTemplate,
            String status
    ) {}

    public record LookupEnvelope(
            String integrationStatus,
            String friendlyMessage,
            Map<String, Object> data
    ) {}
}
