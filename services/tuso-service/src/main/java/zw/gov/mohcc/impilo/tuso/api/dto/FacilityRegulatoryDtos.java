package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.tuso.persistence.entity.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FacilityRegulatoryDtos {

    private FacilityRegulatoryDtos() {
    }

    public record CreateFacilityApplicationRequest(
            Long facilityId,
            @NotNull FacilityApplicationType applicationType,
            String pathway,
            @NotBlank String applicantName,
            String applicantEmail,
            String applicantPhone,
            String applicantOrganisation,
            String priority,
            String feeState,
            String workflowNotes,
            String facilityCode,
            String facilityName,
            List<String> aliasNames,
            String ownershipType,
            String operatingEntity,
            String facilityClass,
            String facilityCategory,
            String facilityType,
            String legalStatus,
            String registrationPathway,
            String province,
            String district,
            String city,
            String ward,
            String postalCode,
            String addressLine1,
            String addressLine2,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            Map<String, Object> metadata,
            @Valid List<UnitDraft> requestedUnits,
            @Valid PractitionerInChargeDraft practitionerInCharge,
            String catalogueCode,
            java.util.UUID premisesId,
            Long facilityUnitId,
            String feeReference,
            String paymentReference
    ) {
    }

    public record UnitDraft(
            @NotBlank String name,
            @NotBlank String unitType,
            String serviceLine,
            boolean registrationRequired,
            Map<String, Object> metadata
    ) {
    }

    public record PractitionerInChargeDraft(
            @NotBlank String providerPublicId,
            String role,
            @NotNull LocalDate startDate,
            String sourceCouncilReference,
            String notes
    ) {
    }

    public record UploadFacilityDocumentRequest(
            Long facilityId,
            UUID applicationId,
            @NotBlank String documentType,
            @NotBlank String fileReference,
            Integer version,
            String verificationState,
            String notes,
            Map<String, Object> metadata
    ) {
    }

    public record ScheduleFacilityInspectionRequest(
            @NotNull Long facilityId,
            UUID applicationId,
            @NotNull FacilityInspectionType inspectionType,
            LocalDate scheduledDate,
            String templateCode,
            String compositionCode,
            List<String> supplementaryModules,
            String supplementJustification,
            @Valid List<InspectorAssignment> inspectorAssignments,
            String notes,
            String captureMode,
            String offlineCaptureReference
    ) {
    }

    public record InspectorAssignment(
            @NotBlank String actorId,
            String displayName,
            String role,
            String authorityContext
    ) {
    }

    public record RecordInspectionOutcomeRequest(
            LocalDate actualDate,
            @Valid List<FindingInput> findings,
            String notes,
            String recommendation,
            String nextAction,
            LocalDate nextInspectionDueDate
    ) {
    }

    public record FindingInput(
            @NotBlank String checklistItemCode,
            String checklistSection,
            @NotBlank String requirementText,
            Boolean criticalFlag,
            @NotNull InspectionFindingStatus status,
            String severity,
            String comments,
            LocalDate rectificationDeadline,
            List<Map<String, Object>> evidenceRefs,
            String ownerName,
            String ownerRole,
            String actionType
    ) {
    }

    public record UpdateComplianceActionRequest(
            @NotNull RectificationStatus status,
            String completionNotes,
            List<Map<String, Object>> completionEvidenceRefs,
            String verificationNotes
    ) {
    }

    public record RecordCommitteeDecisionRequest(
            @NotNull Long facilityId,
            @NotNull UUID applicationId,
            UUID inspectionId,
            @NotBlank String committeeType,
            LocalDate scheduledDate,
            @NotNull CommitteeDecision decision,
            String notes,
            String resolutionReference,
            String authorityContext,
            Boolean issueCertificate,
            String certificateType,
            String digitalArtifactReference,
            Map<String, Object> metadata
    ) {
    }

    public record OpenEnforcementCaseRequest(
            @NotNull Long facilityId,
            UUID applicationId,
            @NotBlank String triggerType,
            EnforcementCaseStatus status,
            String recommendation,
            String authorityRoute,
            List<String> linkedInspectionIds,
            List<Map<String, Object>> linkedEvidence,
            String closureReason,
            String decisionDetails
    ) {
    }

    public record FacilityRegistrySummaryResponse(
            Long facilityId,
            String facilityCode,
            String name,
            String facilityType,
            String status,
            FacilityRegulatoryStatus regulatoryStatus,
            String registrationPathway,
            String province,
            String district,
            String institutionFileNumber,
            String latestCertificateNumber,
            LocalDate latestCertificateExpiryDate,
            long openApplications,
            long overdueActions
    ) {
    }

    public record DashboardSummaryResponse(
            long totalFacilities,
            long activeFacilities,
            long pendingInspection,
            long pendingCommitteeReviews,
            long overdueComplianceActions,
            long renewalsDueSoon,
            long openEnforcementCases,
            Map<String, Long> facilitiesByRegulatoryStatus
    ) {
    }

    public record FacilityRegulatoryProfileResponse(
            FacilityMaster master,
            List<ApplicationView> applications,
            List<DocumentView> documents,
            List<InspectionView> inspections,
            List<FindingView> findings,
            List<ComplianceActionView> complianceActions,
            List<CommitteeReviewView> committeeReviews,
            List<CertificateView> certificates,
            List<UnitView> units,
            List<PractitionerAssignmentView> practitionerAssignments,
            List<StatusHistoryView> statusHistory,
            List<AuditEventView> auditTrail,
            List<EnforcementCaseView> enforcementCases
    ) {
    }

    public record FacilityMaster(
            Long facilityId,
            String facilityCode,
            String name,
            List<String> aliasNames,
            String ownershipType,
            String operatingEntity,
            String facilityClass,
            String facilityCategory,
            String facilityType,
            String legalStatus,
            String registrationPathway,
            String status,
            String operationalStatus,
            FacilityRegulatoryStatus regulatoryStatus,
            String institutionFileNumber,
            String province,
            String district,
            String city,
            String ward,
            String postalCode,
            String addressLine1,
            String addressLine2,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ApplicationView(
            UUID applicationId,
            FacilityApplicationType applicationType,
            String pathway,
            String applicantName,
            Instant submissionDate,
            FacilityApplicationState currentWorkflowState,
            String priority,
            String feeState,
            String committeeState,
            String finalDecision,
            Instant decisionDate,
            String institutionFileNumber,
            Map<String, Object> requestedChanges,
            Map<String, Object> metadata,
            String workflowNotes,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DocumentView(
            UUID documentId,
            String documentType,
            String fileReference,
            Integer version,
            String verificationState,
            String uploadedBy,
            Instant uploadedAt,
            String notes,
            Map<String, Object> metadata
    ) {
    }

    public record InspectionView(
            UUID inspectionId,
            UUID applicationId,
            FacilityInspectionType inspectionType,
            String templateCode,
            String templateName,
            LocalDate scheduledDate,
            LocalDate actualDate,
            List<Map<String, Object>> inspectorAssignments,
            FacilityInspectionStatus status,
            String reportStatus,
            String outcome,
            String recommendation,
            String severitySummary,
            String nextAction,
            LocalDate nextInspectionDueDate,
            String captureMode,
            String offlineCaptureReference,
            Map<String, Object> evidenceSummary,
            String notes
    ) {
    }

    public record FindingView(
            UUID findingId,
            UUID inspectionId,
            String checklistItemCode,
            String checklistSection,
            String requirementText,
            boolean criticalFlag,
            InspectionFindingStatus status,
            String severity,
            String comments,
            List<Map<String, Object>> evidenceRefs,
            LocalDate rectificationDeadline,
            RectificationStatus rectificationStatus
    ) {
    }

    public record ComplianceActionView(
            UUID actionId,
            UUID findingId,
            String actionType,
            String ownerName,
            String ownerRole,
            LocalDate dueDate,
            RectificationStatus status,
            String completionNotes,
            List<Map<String, Object>> completionEvidenceRefs,
            String verifiedBy,
            Instant verifiedAt,
            String verificationNotes
    ) {
    }

    public record CommitteeReviewView(
            UUID reviewId,
            UUID applicationId,
            UUID inspectionId,
            String committeeType,
            LocalDate scheduledDate,
            CommitteeDecision decision,
            String notes,
            String resolutionReference,
            String authorityContext,
            String decidedBy,
            Instant decidedAt,
            Map<String, Object> metadata
    ) {
    }

    public record CertificateView(
            UUID certificateId,
            UUID applicationId,
            String certificateType,
            String certificateNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            FacilityCertificateStatus status,
            String issuedUnderAuthority,
            String digitalArtifactReference,
            UUID supersedesCertificateId,
            Map<String, Object> metadata,
            Instant createdAt,
            String issuedBy
    ) {
    }

    public record UnitView(
            Long unitId,
            String name,
            String unitType,
            String serviceLine,
            boolean registrationRequired,
            FacilityRegulatoryStatus regulatoryStatus,
            String certificateStatus,
            Map<String, Object> metadata
    ) {
    }

    public record PractitionerAssignmentView(
            Long assignmentId,
            String providerPublicId,
            String role,
            LocalDate startDate,
            LocalDate endDate,
            String approvalState,
            String sourceCouncilReference,
            String notes
    ) {
    }

    public record StatusHistoryView(
            Instant changedAt,
            FacilityRegulatoryStatus regulatoryStatus,
            FacilityApplicationState applicationState,
            String changedBy,
            String authorityContext,
            String reason,
            UUID sourceApplicationId
    ) {
    }

    public record AuditEventView(
            UUID auditId,
            Instant createdAt,
            String actorId,
            String actorType,
            String authorityContext,
            String action,
            String targetEntityType,
            String targetEntityId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            String correlationId,
            Map<String, Object> metadata
    ) {
    }

    public record EnforcementCaseView(
            UUID caseId,
            UUID applicationId,
            String triggerType,
            EnforcementCaseStatus status,
            String recommendation,
            String authorityRoute,
            List<String> linkedInspectionIds,
            List<Map<String, Object>> linkedEvidence,
            String closureReason,
            String decisionDetails,
            Instant openedAt,
            Instant closedAt,
            String openedBy,
            String updatedBy
    ) {
    }
}
