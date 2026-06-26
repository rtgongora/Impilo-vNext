package zw.gov.mohcc.impilo.patientsafety.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Full safety case view for the MCAZ workbench: status, timeline, follow-ups, VigiFlow + export state. */
public record CaseResponse(
        UUID id,
        String caseReference,
        String status,
        String priority,
        String reportType,
        boolean serious,
        String assignedReviewerId,
        String causalityAssessment,
        String mcazStatus,
        OffsetDateTime triagedAt,
        OffsetDateTime closedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ReportSummary report,
        List<TimelineEntry> timeline,
        List<FollowUpView> followUps,
        List<VigiFlowView> vigiflowSubmissions,
        ExportView latestExport,
        InvestigationView investigation
) {
    public record TimelineEntry(
            UUID id, String actionType, String actorId, String actorRole,
            String fromStatus, String toStatus, String note, OffsetDateTime createdAt) {}

    public record FollowUpView(
            UUID id, String requestText, String channelHint, String conversationRef,
            String status, LocalDate dueDate, OffsetDateTime createdAt, OffsetDateTime respondedAt,
            List<FollowUpResponseView> responses) {}

    public record FollowUpResponseView(
            UUID id, String responderId, String responderKind, String responseText,
            String attachmentObjectId, OffsetDateTime createdAt) {}

    public record VigiFlowView(
            UUID id, String entryMode, String vigiflowReferenceId, String enteredBy,
            String note, OffsetDateTime enteredAt) {}

    /** Export readiness — note adapterEnabled / status are surfaced honestly (never "submitted"). */
    public record ExportView(
            UUID id, String packageFormat, String status, String adapterKey,
            boolean adapterEnabled, String message, OffsetDateTime createdAt) {}

    public record InvestigationView(
            UUID id, String investigationReference, String status, String assignedTo,
            LocalDate plannedDate, String finalClassification, String summary,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
}
