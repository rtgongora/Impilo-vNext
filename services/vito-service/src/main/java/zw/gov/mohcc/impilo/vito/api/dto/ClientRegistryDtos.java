package zw.gov.mohcc.impilo.vito.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.vito.core.ClientMatchReviewStatus;
import zw.gov.mohcc.impilo.vito.core.ClientRegistrationType;
import zw.gov.mohcc.impilo.vito.core.ClientRegistrationWorkflowState;
import zw.gov.mohcc.impilo.vito.core.ClientRelationshipType;
import zw.gov.mohcc.impilo.vito.core.ClientStewardshipActionType;
import zw.gov.mohcc.impilo.vito.core.ClientStewardshipStatus;
import zw.gov.mohcc.impilo.vito.core.ClientVerificationState;
import zw.gov.mohcc.impilo.vito.core.IdentityStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientRegistryDtos {

    private ClientRegistryDtos() {
    }

    public record CreateRegistrationRequest(
            UUID clientHealthId,
            @NotNull ClientRegistrationType registrationType,
            @NotBlank String initiatedChannel,
            String linkedProviderId,
            Long linkedFacilityId,
            String linkedServiceContextId,
            String firstName,
            String middleName,
            String lastName,
            LocalDate dateOfBirth,
            Boolean estimatedDateOfBirth,
            String sex,
            String phone,
            String email,
            String nationalIdReference,
            String passportReference,
            // Optional medical-aid membership number, attached to the Health ID as an identifier
            // (mirrors linkedProviderId being optionally tied to the person).
            String medicalAidNumber,
            String addressLine1,
            String addressLine2,
            String city,
            String district,
            String province,
            String notes,
            Map<String, Object> metadata,
            List<String> previousNames,
            String preferredLanguage,
            String maritalStatus,
            String emergencyContactName,
            String emergencyContactPhone,
            Boolean issueProvisionalIdentifier
    ) {
    }

    public record AddEvidenceRequest(
            @NotNull UUID registrationId,
            @NotBlank String evidenceType,
            @NotBlank String evidenceReference,
            ClientVerificationState verificationState,
            String notes
    ) {
    }

    public record RecordVerificationReviewRequest(
            UUID registrationId,
            @NotBlank String reviewType,
            @NotBlank String decision,
            String notes,
            Integer identityAssuranceLevel,
            Boolean issueImpiloId
    ) {
    }

    public record ReviewMatchCandidateRequest(
            @NotNull ClientMatchReviewStatus outcome,
            String notes
    ) {
    }

    public record CreateMergeCaseRequest(
            @NotNull UUID primaryHealthId,
            @NotNull UUID secondaryHealthId,
            Long linkedMatchId,
            Long linkedDedupCaseId,
            String survivorshipSummary
    ) {
    }

    public record DecideMergeCaseRequest(
            @NotBlank String decision,
            String survivorshipSummary
    ) {
    }

    public record AddRelationshipRequest(
            @NotNull UUID relatedClientHealthId,
            @NotNull ClientRelationshipType relationshipType,
            LocalDate startDate,
            LocalDate endDate,
            String notes
    ) {
    }

    public record AddAuthorizationLinkRequest(
            @NotBlank String authorisationType,
            @NotBlank String referenceId,
            String status,
            OffsetDateTime startDate,
            OffsetDateTime endDate
    ) {
    }

    public record CreateStewardshipActionRequest(
            UUID relatedRegistrationId,
            UUID relatedMergeCaseId,
            @NotNull ClientStewardshipActionType actionType,
            String owner,
            OffsetDateTime dueDate,
            String completionNotes
    ) {
    }

    public record UpdateStewardshipActionRequest(
            @NotNull ClientStewardshipStatus status,
            String completionNotes,
            String verifiedBy
    ) {
    }

    public record RecordCorrectionRequest(
            String firstName,
            String middleName,
            String lastName,
            LocalDate dateOfBirth,
            String sex,
            String phone,
            String email,
            String notes,
            boolean requireReview
    ) {
    }

    public record ClientRegistrySummaryResponse(
            UUID healthId,
            UUID crid,
            String impiloId,
            String displayName,
            IdentityStatus lifecycleStatus,
            ClientVerificationState verificationStatus,
            int identityAssuranceLevel,
            boolean goldenRecord,
            boolean active,
            boolean deceased,
            String latestRegistrationType,
            String latestRegistrationChannel,
            long openStewardshipActions,
            long openMatches
    ) {
    }

    public record DashboardSummaryResponse(
            long totalClients,
            long provisionalClients,
            long pendingVerification,
            long pendingMatchReview,
            long openStewardshipActions,
            long mergeCasesOpen,
            Map<String, Long> clientsByStatus
    ) {
    }

    public record ClientProfileResponse(
            ClientMaster master,
            List<RegistrationView> registrations,
            List<IdentifierView> identifiers,
            List<AliasView> aliases,
            List<EvidenceView> evidence,
            List<VerificationReviewView> verificationReviews,
            List<MatchCandidateView> matchCandidates,
            List<MergeCaseView> mergeCases,
            List<RelationshipView> relationships,
            List<AuthorizationLinkView> authorizationLinks,
            List<StewardshipActionView> stewardshipActions,
            List<StatusHistoryView> statusHistory,
            List<AuditEventView> auditTrail
    ) {
    }

    public record ClientMaster(
            UUID healthId,
            UUID crid,
            String impiloId,
            String firstName,
            String middleName,
            String lastName,
            LocalDate dateOfBirth,
            String sex,
            IdentityStatus lifecycleStatus,
            ClientVerificationState verificationStatus,
            int identityAssuranceLevel,
            boolean activeFlag,
            boolean deceasedFlag,
            boolean goldenRecordFlag,
            Map<String, Object> demographics,
            Map<String, Object> contacts,
            Map<String, Object> address,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record RegistrationView(
            UUID registrationId,
            ClientRegistrationType registrationType,
            String initiatedChannel,
            String initiatedBy,
            Long linkedFacilityId,
            String linkedProviderId,
            String linkedServiceContextId,
            ClientRegistrationWorkflowState workflowState,
            String completionState,
            ClientVerificationState verificationState,
            OffsetDateTime submissionDate,
            String notes,
            Map<String, Object> provenance
    ) {
    }

    public record IdentifierView(
            UUID identifierId,
            String identifierType,
            String identifierValue,
            String status,
            boolean primaryFlag,
            OffsetDateTime issueDate,
            OffsetDateTime expiryDate,
            String source
    ) {
    }

    public record AliasView(
            UUID aliasId,
            String aliasType,
            String aliasValue,
            String source,
            BigDecimal confidence,
            boolean activeFlag
    ) {
    }

    public record EvidenceView(
            UUID evidenceId,
            UUID registrationId,
            String evidenceType,
            String evidenceReference,
            ClientVerificationState verificationState,
            String verifiedBy,
            OffsetDateTime verifiedAt,
            String notes
    ) {
    }

    public record VerificationReviewView(
            UUID reviewId,
            UUID registrationId,
            String reviewType,
            String status,
            String reviewer,
            String decision,
            String notes,
            OffsetDateTime reviewedAt
    ) {
    }

    public record MatchCandidateView(
            Long matchId,
            UUID sourceHealthId,
            UUID candidateHealthId,
            BigDecimal matchScore,
            String matchReasonSummary,
            ClientMatchReviewStatus status,
            OffsetDateTime generatedAt,
            String resolvedBy,
            OffsetDateTime resolvedAt
    ) {
    }

    public record MergeCaseView(
            UUID mergeCaseId,
            UUID primaryHealthId,
            UUID secondaryHealthId,
            String initiatedBy,
            String reviewedBy,
            String decision,
            String survivorshipSummary,
            String status,
            OffsetDateTime executedAt,
            Long mergeHistoryId
    ) {
    }

    public record RelationshipView(
            UUID relationshipId,
            UUID clientHealthId,
            UUID relatedClientHealthId,
            ClientRelationshipType relationshipType,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String notes
    ) {
    }

    public record AuthorizationLinkView(
            UUID authorizationLinkId,
            String authorisationType,
            String referenceId,
            String status,
            OffsetDateTime startDate,
            OffsetDateTime endDate
    ) {
    }

    public record StewardshipActionView(
            UUID actionId,
            ClientStewardshipActionType actionType,
            String owner,
            OffsetDateTime dueDate,
            ClientStewardshipStatus status,
            String completionNotes,
            String verifiedBy,
            OffsetDateTime verifiedAt,
            UUID relatedRegistrationId,
            UUID relatedMergeCaseId
    ) {
    }

    public record StatusHistoryView(
            UUID statusHistoryId,
            String previousStatus,
            String newStatus,
            String reason,
            String changedBy,
            OffsetDateTime changedAt,
            Map<String, Object> provenanceContext
    ) {
    }

    public record AuditEventView(
            UUID auditEventId,
            String actor,
            String actorType,
            String action,
            String targetEntity,
            String targetId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            String correlationId,
            Map<String, Object> provenanceContext,
            OffsetDateTime createdAt
    ) {
    }

    public record StewardshipWorkspaceResponse(
            List<MatchCandidateView> duplicateQueue,
            List<VerificationReviewView> verificationQueue,
            List<StewardshipActionView> stewardshipQueue
    ) {
    }
}
