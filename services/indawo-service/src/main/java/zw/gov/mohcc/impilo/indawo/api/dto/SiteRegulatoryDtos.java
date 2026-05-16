package zw.gov.mohcc.impilo.indawo.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SiteRegulatoryDtos {

    private SiteRegulatoryDtos() {}

    // ---------------------------------------------------------------------
    // Search/list surfaces
    // ---------------------------------------------------------------------
    public record SiteRegistrySummaryResponse(
            UUID siteId,
            String siteCode,
            String name,
            String siteType,
            String siteCategory,
            String province,
            String district,
            String regulatoryStatus,
            String lifecycleStatus,
            String licenceStatus,
            LocalDate licenceExpiryDate
    ) {}

    public record InspectionRegisterRow(
            UUID inspectionId,
            UUID siteId,
            String siteName,
            String siteType,
            String inspectionType,
            LocalDate scheduledDate,
            LocalDate actualDate,
            String status,
            String outcome,
            BigDecimal scorePercent,
            Integer criticalFailCount
    ) {}

    public record ComplianceActionRow(
            UUID actionId,
            UUID siteId,
            String siteName,
            String siteType,
            String actionType,
            String ownerRef,
            LocalDate dueDate,
            String status
    ) {}

    public record EnforcementCaseRow(
            UUID caseId,
            UUID siteId,
            String siteName,
            String siteType,
            String triggerType,
            String status,
            String recommendation,
            String authorityRoute
    ) {}

    // ---------------------------------------------------------------------
    // Profile (site + operational lifecycle)
    // ---------------------------------------------------------------------
    public record SiteRegulatoryProfileResponse(
            SiteView site,
            List<ApplicationView> applications,
            List<OperatorView> operators,
            List<InspectionView> inspections,
            List<FindingView> findings,
            List<ComplianceActionView> complianceActions,
            List<DocumentView> documents,
            List<LicenceView> licences,
            List<EnforcementCaseView> enforcementCases,
            List<AssignmentView> assignments,
            List<StatusHistoryView> statusHistory,
            List<AuditEventView> auditEvents
    ) {}

    public record SiteView(
            UUID siteId,
            String siteCode,
            String name,
            String type,
            String siteCategory,
            String riskClass,
            String province,
            String district,
            String ward,
            String regulatoryStatus,
            String lifecycleStatus,
            boolean activeFlag
    ) {}

    public record ApplicationView(
            UUID applicationId,
            UUID siteId,
            String applicationType,
            String workflowState,
            String priority,
            String feeState,
            String finalDecision,
            String applicantName,
            String notes
    ) {}

    public record OperatorView(
            UUID operatorId,
            UUID siteId,
            String operatorType,
            String operatorName,
            String contactPhone,
            String contactEmail,
            boolean activeFlag
    ) {}

    public record InspectionView(
            UUID inspectionId,
            UUID siteId,
            UUID applicationId,
            String inspectionType,
            LocalDate scheduledDate,
            LocalDate actualDate,
            String status,
            String reportStatus,
            String outcome,
            String severitySummary,
            UUID templateId,
            BigDecimal scoreTotal,
            BigDecimal scoreMax,
            BigDecimal scorePercent,
            Integer criticalFailCount,
            Integer majorFailCount,
            Integer minorFailCount
    ) {}

    public record FindingView(
            UUID findingId,
            UUID inspectionId,
            UUID checklistItemId,
            String status,
            String severity,
            String comments,
            Object evidenceRefs,
            LocalDate rectificationDeadline,
            String rectificationStatus
    ) {}

    public record ComplianceActionView(
            UUID actionId,
            UUID siteId,
            UUID findingId,
            String actionType,
            String ownerRef,
            LocalDate dueDate,
            String status,
            String completionNotes
    ) {}

    public record DocumentView(
            UUID documentId,
            UUID siteId,
            UUID applicationId,
            String documentType,
            String fileReference,
            String verificationState,
            String uploadedBy,
            String uploadedAt,
            String reviewedBy,
            String reviewedAt,
            String notes
    ) {}

    public record LicenceView(
            UUID licenceId,
            UUID siteId,
            String licenceType,
            LocalDate issueDate,
            LocalDate expiryDate,
            String status,
            String restrictionSummary
    ) {}

    public record EnforcementCaseView(
            UUID caseId,
            UUID siteId,
            String triggerType,
            String status,
            String recommendation,
            String authorityRoute
    ) {}

    public record AssignmentView(
            UUID assignmentId,
            UUID siteId,
            String assignmentType,
            String assignedRole,
            String assignedToRef,
            String jurisdictionRef,
            String status,
            String priority,
            LocalDate dueDate,
            String notes
    ) {}

    public record StatusHistoryView(
            UUID id,
            String previousStatus,
            String newStatus,
            String reason,
            String changedBy
    ) {}

    public record AuditEventView(
            UUID auditId,
            String actorRef,
            String actorType,
            String action,
            String targetType,
            String targetId,
            String notes
    ) {}

    // ---------------------------------------------------------------------
    // Commands (workflows)
    // ---------------------------------------------------------------------
    public record CreateSiteApplicationRequest(
            UUID siteId,
            @NotNull String applicationType,
            @NotBlank String applicantName,
            String applicantEmail,
            String applicantPhone,
            String applicantOrg,
            String notes,
            SiteDraft siteDraft,
            OperatorDraft operatorDraft
    ) {}

    public record SiteDraft(
            @NotBlank String name,
            @NotBlank String type,
            String siteCode,
            String siteCategory,
            String riskClass,
            String province,
            String district,
            String ward
    ) {}

    public record OperatorDraft(
            @NotBlank String operatorType,
            @NotBlank String operatorName,
            String contactPhone,
            String contactEmail
    ) {}

    public record ScheduleSiteInspectionRequest(
            @NotNull UUID siteId,
            UUID applicationId,
            @NotBlank String inspectionType,
            @NotNull LocalDate scheduledDate,
            String templateCode,
            List<String> inspectorRefs
    ) {}

    public record RecordSiteInspectionOutcomeRequest(
            @NotNull LocalDate actualDate,
            @NotBlank String outcome,
            String recommendation,
            List<ChecklistFinding> findings,
            LocalDate nextInspectionDue
    ) {}

    public record ChecklistFinding(
            UUID checklistItemId,
            @NotBlank String status,
            String severity,
            String comments,
            Map<String, Object> evidenceRefs,
            LocalDate rectificationDeadline,
            boolean createComplianceAction
    ) {}

    public record UpdateComplianceActionRequest(
            @NotBlank String status,
            String completionNotes
    ) {}

    public record VerifyDocumentRequest(
            @NotBlank String verificationState,
            String notes
    ) {}

    public record CreateAssignmentRequest(
            @NotNull UUID siteId,
            @NotBlank String assignmentType,
            String assignedRole,
            String assignedToRef,
            String jurisdictionRef,
            String priority,
            LocalDate dueDate,
            String notes
    ) {}

    public record UpdateAssignmentRequest(
            @NotBlank String status,
            String notes
    ) {}

    public record IssueLicenceRequest(
            @NotNull UUID siteId,
            UUID applicationId,
            @NotBlank String licenceType,
            @NotNull LocalDate issueDate,
            @NotNull LocalDate expiryDate,
            String restrictionSummary
    ) {}

    public record OpenEnforcementCaseRequest(
            @NotNull UUID siteId,
            @NotBlank String triggerType,
            String recommendation,
            String authorityRoute,
            Map<String, Object> linkedRefs
    ) {}
}

