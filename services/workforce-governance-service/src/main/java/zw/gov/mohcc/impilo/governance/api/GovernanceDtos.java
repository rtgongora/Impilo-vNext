package zw.gov.mohcc.impilo.governance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public final class GovernanceDtos {

    private GovernanceDtos() {}

    public record CreateOrganisationRequest(
            @NotBlank String organisationCode,
            @NotBlank String name,
            String legalName,
            @NotBlank String organisationType,
            UUID parentOrganisationId,
            String metadataJson
    ) {}

    public record OrganisationStatusRequest(@NotBlank String status) {}

    public record CreateOrgUnitRequest(
            @NotNull UUID organisationId,
            UUID parentUnitId,
            @NotBlank String unitCode,
            @NotBlank String name,
            @NotBlank String unitType
    ) {}

    public record CreateJurisdictionRequest(
            @NotBlank String jurisdictionCode,
            @NotBlank String name,
            @NotBlank String jurisdictionType,
            UUID parentJurisdictionId
    ) {}

    public record CreateJurisdictionLinkRequest(
            @NotBlank String targetType,
            @NotBlank String targetId,
            @NotNull UUID jurisdictionId,
            @NotBlank String relationshipType,
            boolean primary,
            String startDate,
            String endDate
    ) {}

    public record CreateRoleDefinitionRequest(
            @NotBlank String roleCode,
            @NotBlank String name,
            String description,
            @NotBlank String roleCategory,
            @NotBlank String roleLevel,
            @NotNull List<String> allowedTargetTypes,
            boolean requiresProvider,
            boolean requiresProfessionalStanding,
            boolean specialGovernance
    ) {}

    public record CreateFacilityLinkRequest(
            @NotNull Long facilityId,
            @NotNull UUID organisationId,
            UUID organisationUnitId,
            @NotBlank String relationshipType,
            boolean primary,
            String startDate,
            String endDate
    ) {}

    public record CreateSiteLinkRequest(
            @NotNull UUID siteId,
            @NotNull UUID organisationId,
            UUID organisationUnitId,
            @NotBlank String relationshipType,
            boolean primary,
            String startDate,
            String endDate
    ) {}

    public record CreateMultiSiteGroupRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String groupType,
            UUID organisationId
    ) {}

    public record AddGroupMemberRequest(
            @NotBlank String memberTargetType,
            @NotBlank String memberTargetId,
            String startDate,
            String endDate
    ) {}

    public record CreateAssignmentRequest(
            @NotBlank String subjectType,
            @NotBlank String subjectId,
            @NotNull UUID roleDefinitionId,
            @NotBlank String targetType,
            @NotBlank String targetId,
            UUID organisationId,
            UUID organisationUnitId,
            UUID jurisdictionId,
            @NotBlank String initialStatus,
            boolean primary,
            boolean secondary,
            String scopeSummary,
            String authorityLevel,
            String reportingLineRef,
            String notes,
            String extensionJson
    ) {}

    public record AssignmentTransitionRequest(@NotBlank String newStatus, String reason) {}

    public record AssignmentSearchParams(String subjectType, String subjectId, String status) {}

    public record EvaluateFacilityScopeRequest(
            @NotBlank String actorHealthId,
            String providerPublicId,
            @NotNull Long tusoFacilityId
    ) {}

    public record SingleFacilityOnboardRequest(
            @NotBlank String organisationCode,
            @NotBlank String organisationName,
            String legalName,
            @NotBlank String organisationType,
            @NotNull Long facilityId,
            boolean createDefaultUnit
    ) {}

    public record MultiFacilityOnboardRequest(
            @NotBlank String organisationCode,
            @NotBlank String organisationName,
            String legalName,
            @NotBlank String organisationType,
            @NotNull List<Long> facilityIds,
            boolean createDefaultUnit
    ) {}

    public record PatchOrganisationRequest(String name, String legalName, String metadataJson) {}

    public record CreateAccessRequestBody(
            @NotBlank String requestType,
            String status,
            @NotBlank String requesterId,
            String requesterName,
            UUID organisationId,
            String targetSubjectId,
            String requestedRole,
            String requestedScope,
            String requestedEnvironment,
            String requestedDataScope,
            String riskLevel,
            String policyPrecheckResult,
            Object approvalsRequired,
            Object payload,
            String fallbackRequestId
    ) {}

    public record AccessRequestDecisionBody(String notes, String actorId) {}

    public record OrganisationMemberRequest(
            @NotBlank String userId,
            String subjectType,
            String roleTemplate,
            String status,
            String metadata
    ) {}

    public record UpsertHscEmploymentRequest(
            String employmentRecordId,
            String providerWorkerId,
            String linkedHealthId,
            String ecNumber,
            UUID employerOrganisationId,
            String employmentStatus,
            String postId,
            String postTitle,
            String grade,
            String cadre,
            String establishmentUnit,
            String currentPostingProvince,
            String currentPostingDistrict,
            String currentPostingFacility,
            String currentPostingDepartment,
            String transferStatus,
            String promotionStatus,
            String disciplinaryEmploymentStatus,
            String verificationStatus
    ) {}

    public record CreateProgrammeRequest(
            @NotBlank String programmeCode,
            @NotBlank String name,
            @NotBlank String programmeType,
            @NotBlank String scopeLevel,
            UUID parentProgrammeId,
            UUID responsibleOrganisationId,
            String accountableOffice,
            String validFrom,
            String validTo,
            String description
    ) {}

    public record ProgrammeStatusRequest(@NotBlank String status) {}

    public record LinkProgrammeJurisdictionRequest(
            @NotNull UUID jurisdictionId,
            String startDate,
            String endDate
    ) {}

    public record LinkProgrammeFacilityRequest(
            @NotBlank String facilityId,
            String startDate,
            String endDate
    ) {}

    public record AddProgrammeServiceRequest(
            @NotBlank String serviceCode,
            String serviceLabel
    ) {}
}
