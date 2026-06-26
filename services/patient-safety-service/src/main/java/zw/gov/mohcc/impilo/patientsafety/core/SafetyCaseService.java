package zw.gov.mohcc.impilo.patientsafety.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.patientsafety.api.dto.CaseResponse;
import zw.gov.mohcc.impilo.patientsafety.api.dto.CaseSummary;
import zw.gov.mohcc.impilo.patientsafety.api.dto.FollowUpRequestDto;
import zw.gov.mohcc.impilo.patientsafety.api.dto.FollowUpResponseDto;
import zw.gov.mohcc.impilo.patientsafety.api.dto.ReviewActionRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.TriageRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.VigiFlowManualEntryRequest;
import zw.gov.mohcc.impilo.patientsafety.core.PatientSafetyExceptions.ConflictException;
import zw.gov.mohcc.impilo.patientsafety.core.PatientSafetyExceptions.NotFoundException;
import zw.gov.mohcc.impilo.patientsafety.domain.CaseActionEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.ConversationLinkEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.E2bExportAttemptEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.FollowUpRequestEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.FollowUpResponseEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.SafetyCaseEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.SafetyReportEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.VigiFlowSubmissionEntity;
import zw.gov.mohcc.impilo.patientsafety.repository.AefiInvestigationRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.CaseActionRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.ConversationLinkRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.E2bExportAttemptRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.FollowUpRequestRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.FollowUpResponseRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.ProductExposureRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.SafetyCaseRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.SafetyReportRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.VigiFlowSubmissionRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lifecycle for regulatory safety cases (MCAZ workbench). A case is opened when a report is
 * submitted; the MCAZ then triages, reviews, requests follow-up via the Comms Hub, records a MANUAL
 * VigiFlow reference, and marks an E2B(R3)-aligned export package ready. No automated external
 * submission happens here.
 */
@Service
public class SafetyCaseService {

    private final SafetyCaseRepository caseRepository;
    private final CaseActionRepository actionRepository;
    private final FollowUpRequestRepository followUpRequestRepository;
    private final FollowUpResponseRepository followUpResponseRepository;
    private final VigiFlowSubmissionRepository vigiFlowRepository;
    private final E2bExportAttemptRepository exportRepository;
    private final ConversationLinkRepository conversationLinkRepository;
    private final AefiInvestigationRepository investigationRepository;
    private final SafetyReportRepository reportRepository;
    private final ProductExposureRepository productRepository;
    private final SafetyEventService events;

    public SafetyCaseService(SafetyCaseRepository caseRepository,
                             CaseActionRepository actionRepository,
                             FollowUpRequestRepository followUpRequestRepository,
                             FollowUpResponseRepository followUpResponseRepository,
                             VigiFlowSubmissionRepository vigiFlowRepository,
                             E2bExportAttemptRepository exportRepository,
                             ConversationLinkRepository conversationLinkRepository,
                             AefiInvestigationRepository investigationRepository,
                             SafetyReportRepository reportRepository,
                             ProductExposureRepository productRepository,
                             SafetyEventService events) {
        this.caseRepository = caseRepository;
        this.actionRepository = actionRepository;
        this.followUpRequestRepository = followUpRequestRepository;
        this.followUpResponseRepository = followUpResponseRepository;
        this.vigiFlowRepository = vigiFlowRepository;
        this.exportRepository = exportRepository;
        this.conversationLinkRepository = conversationLinkRepository;
        this.investigationRepository = investigationRepository;
        this.reportRepository = reportRepository;
        this.productRepository = productRepository;
        this.events = events;
    }

    // ---- case creation (called from report submit) -------------------------

    @Transactional
    public SafetyCaseEntity openCaseForReport(SafetyReportEntity report, UUID correlationId) {
        String priority = derivePriority(report.getReportType(), report.isSerious());
        String reference = nextCaseReference(report.getTenantId());
        SafetyCaseEntity c = new SafetyCaseEntity(
                report.getTenantId(), report.getPodId(), report.getId(), reference,
                report.getReportType(), report.isSerious(), priority);
        c.setMcazStatus("Received — awaiting triage");
        caseRepository.save(c);

        CaseActionEntity opened = new CaseActionEntity(c.getId(), c.getTenantId(), "STATUS_CHANGE");
        opened.setToStatus("OPEN");
        opened.setNote("Case opened from submitted report " + report.getReferenceCode());
        actionRepository.save(opened);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("case_id", c.getId().toString());
        payload.put("case_reference", c.getCaseReference());
        payload.put("report_id", report.getId().toString());
        payload.put("report_type", c.getReportType());
        payload.put("priority", c.getPriority());
        payload.put("is_serious", c.isSerious());
        events.emitCaseOpened(c.getId(), correlationId, c.getTenantId(), c.getPodId(),
                subjectId(report), payload);
        return c;
    }

    // ---- queries ------------------------------------------------------------

    @Transactional(readOnly = true)
    public CaseResponse getCase(UUID tenantId, UUID caseId) {
        SafetyCaseEntity c = requireCase(tenantId, caseId);
        return toCaseResponse(c);
    }

    @Transactional(readOnly = true)
    public List<CaseSummary> listCases(UUID tenantId, String status, String priority) {
        List<SafetyCaseEntity> cases;
        if (status != null && !status.isBlank()) {
            cases = caseRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        } else if (priority != null && !priority.isBlank()) {
            cases = caseRepository.findByTenantIdAndPriorityOrderByCreatedAtDesc(tenantId, priority);
        } else {
            cases = caseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        return cases.stream().map(this::toCaseSummary).toList();
    }

    /** Incoming queue: not-yet-triaged cases (OPEN). */
    @Transactional(readOnly = true)
    public List<CaseSummary> incomingQueue(UUID tenantId) {
        return caseRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "OPEN")
                .stream().map(this::toCaseSummary).toList();
    }

    /** Priority queue: serious / high-priority cases still in progress. */
    @Transactional(readOnly = true)
    public List<CaseSummary> priorityQueue(UUID tenantId) {
        List<String> open = List.of("OPEN", "TRIAGED", "FOLLOW_UP_REQUESTED", "AWAITING_FOLLOW_UP",
                "UNDER_REVIEW", "READY_FOR_VIGIFLOW");
        return caseRepository.findByTenantIdAndStatusInOrderByCreatedAtDesc(tenantId, open)
                .stream()
                .filter(c -> "URGENT".equals(c.getPriority()) || "HIGH".equals(c.getPriority()))
                .map(this::toCaseSummary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> workbenchStats(UUID tenantId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("incoming", caseRepository.countByTenantIdAndStatus(tenantId, "OPEN"));
        stats.put("triaged", caseRepository.countByTenantIdAndStatus(tenantId, "TRIAGED"));
        stats.put("awaiting_follow_up", caseRepository.countByTenantIdAndStatus(tenantId, "AWAITING_FOLLOW_UP"));
        stats.put("manual_entry_completed", caseRepository.countByTenantIdAndStatus(tenantId, "MANUAL_ENTRY_COMPLETED"));
        stats.put("export_ready", caseRepository.countByTenantIdAndStatus(tenantId, "EXPORT_READY"));
        stats.put("closed", caseRepository.countByTenantIdAndStatus(tenantId, "CLOSED"));
        return stats;
    }

    // ---- MCAZ actions -------------------------------------------------------

    @Transactional
    public CaseResponse triage(UUID tenantId, UUID caseId, String actorId, String actorRole,
                               UUID correlationId, TriageRequest req) {
        SafetyCaseEntity c = requireCase(tenantId, caseId);
        if ("CLOSED".equals(c.getStatus())) {
            throw new ConflictException("Case is closed and cannot be triaged");
        }
        String from = c.getStatus();
        if (req.priority() != null && !req.priority().isBlank()) {
            c.setPriority(req.priority());
        }
        if (req.assignedReviewerId() != null) {
            c.setAssignedReviewerId(req.assignedReviewerId());
        }
        c.setMcazStatus(req.mcazStatus() != null ? req.mcazStatus() : "Triaged");
        c.setStatus("TRIAGED");
        c.setTriagedAt(OffsetDateTime.now());
        c.touch();
        caseRepository.save(c);

        recordAction(c, "TRIAGE", actorId, actorRole, from, "TRIAGED", req.note());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("case_reference", c.getCaseReference());
        payload.put("priority", c.getPriority());
        payload.put("assigned_reviewer_id", c.getAssignedReviewerId());
        events.emitCaseTriaged(c.getId(), correlationId, c.getTenantId(), c.getPodId(),
                subjectId(c), payload);
        return toCaseResponse(c);
    }

    @Transactional
    public CaseResponse addReviewAction(UUID tenantId, UUID caseId, String actorId, String actorRole,
                                        ReviewActionRequest req) {
        SafetyCaseEntity c = requireCase(tenantId, caseId);
        if ("CLOSED".equals(c.getStatus())) {
            throw new ConflictException("Case is closed");
        }
        String from = c.getStatus();
        String actionType = req.actionType() != null ? req.actionType() : "REVIEW_NOTE";
        if (req.causalityAssessment() != null && !req.causalityAssessment().isBlank()) {
            c.setCausalityAssessment(req.causalityAssessment());
            actionType = "CAUSALITY_ASSESSED";
        }
        if (req.mcazStatus() != null) {
            c.setMcazStatus(req.mcazStatus());
        }
        String to = null;
        if (req.toStatus() != null && !req.toStatus().isBlank()) {
            c.setStatus(req.toStatus());
            to = req.toStatus();
        }
        c.touch();
        caseRepository.save(c);
        recordAction(c, actionType, actorId, actorRole, from, to, req.note());
        return toCaseResponse(c);
    }

    @Transactional
    public CaseResponse requestFollowUp(UUID tenantId, UUID caseId, String actorId, String actorRole,
                                        UUID correlationId, FollowUpRequestDto req) {
        SafetyCaseEntity c = requireCase(tenantId, caseId);
        if ("CLOSED".equals(c.getStatus())) {
            throw new ConflictException("Case is closed");
        }
        String from = c.getStatus();
        FollowUpRequestEntity fu = new FollowUpRequestEntity(c.getId(), c.getTenantId(), req.requestText());
        fu.setRequestedBy(actorId);
        if (req.channelHint() != null && !req.channelHint().isBlank()) {
            fu.setChannelHint(req.channelHint());
        }
        fu.setDueDate(req.dueDate());
        if (req.conversationRef() != null && !req.conversationRef().isBlank()) {
            fu.setConversationRef(req.conversationRef());
            fu.setStatus("SENT");
            conversationLinkRepository.save(new ConversationLinkEntity(
                    c.getId(), c.getTenantId(), req.conversationRef(), "FOLLOW_UP"));
        }
        followUpRequestRepository.save(fu);

        c.setStatus("AWAITING_FOLLOW_UP");
        c.setMcazStatus("Awaiting reporter follow-up");
        c.touch();
        caseRepository.save(c);
        recordAction(c, "FOLLOW_UP_REQUESTED", actorId, actorRole, from, "AWAITING_FOLLOW_UP", req.requestText());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("case_reference", c.getCaseReference());
        payload.put("follow_up_request_id", fu.getId().toString());
        payload.put("channel_hint", fu.getChannelHint());
        payload.put("conversation_ref", fu.getConversationRef());
        events.emitFollowUpRequested(c.getId(), correlationId, c.getTenantId(), c.getPodId(),
                subjectId(c), payload);
        return toCaseResponse(c);
    }

    @Transactional
    public CaseResponse recordFollowUpResponse(UUID tenantId, UUID caseId, UUID followUpRequestId,
                                               UUID correlationId, FollowUpResponseDto req) {
        SafetyCaseEntity c = requireCase(tenantId, caseId);
        FollowUpRequestEntity fu = followUpRequestRepository.findByIdAndTenantId(followUpRequestId, tenantId)
                .orElseThrow(() -> new NotFoundException("Follow-up request not found"));
        if (!fu.getCaseId().equals(caseId)) {
            throw new ConflictException("Follow-up request does not belong to this case");
        }
        FollowUpResponseEntity resp = new FollowUpResponseEntity(
                fu.getId(), c.getId(), c.getTenantId(), req.responseText());
        resp.setResponderId(req.responderId());
        resp.setResponderKind(req.responderKind());
        resp.setAttachmentObjectId(req.attachmentObjectId());
        followUpResponseRepository.save(resp);

        fu.setStatus("RESPONDED");
        fu.setRespondedAt(OffsetDateTime.now());
        followUpRequestRepository.save(fu);

        String from = c.getStatus();
        c.setStatus("UNDER_REVIEW");
        c.setMcazStatus("Follow-up received — under review");
        c.touch();
        caseRepository.save(c);
        recordAction(c, "FOLLOW_UP_RECEIVED", req.responderId(), req.responderKind(),
                from, "UNDER_REVIEW", "Follow-up response attached");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("case_reference", c.getCaseReference());
        payload.put("follow_up_request_id", fu.getId().toString());
        payload.put("follow_up_response_id", resp.getId().toString());
        events.emitFollowUpReceived(c.getId(), correlationId, c.getTenantId(), c.getPodId(),
                subjectId(c), payload);
        return toCaseResponse(c);
    }

    @Transactional
    public CaseResponse recordVigiFlowManualEntry(UUID tenantId, UUID caseId, String actorId, String actorRole,
                                                  UUID correlationId, VigiFlowManualEntryRequest req) {
        SafetyCaseEntity c = requireCase(tenantId, caseId);
        if ("CLOSED".equals(c.getStatus())) {
            throw new ConflictException("Case is closed");
        }
        VigiFlowSubmissionEntity rec = new VigiFlowSubmissionEntity(
                c.getId(), c.getTenantId(), req.vigiflowReferenceId());
        rec.setEnteredBy(actorId);
        rec.setNote(req.note());
        vigiFlowRepository.save(rec);

        String from = c.getStatus();
        c.setStatus("MANUAL_ENTRY_COMPLETED");
        c.setMcazStatus("Manual VigiFlow entry recorded: " + req.vigiflowReferenceId());
        c.touch();
        caseRepository.save(c);
        recordAction(c, "VIGIFLOW_MANUAL_ENTRY", actorId, actorRole, from, "MANUAL_ENTRY_COMPLETED",
                "VigiFlow reference " + req.vigiflowReferenceId() + " (manual entry)");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("case_reference", c.getCaseReference());
        payload.put("vigiflow_reference_id", req.vigiflowReferenceId());
        payload.put("entry_mode", "MANUAL");
        events.emitVigiFlowRecorded(c.getId(), correlationId, c.getTenantId(), c.getPodId(),
                subjectId(c), payload);
        return toCaseResponse(c);
    }

    @Transactional
    public CaseResponse markExportReady(UUID tenantId, UUID caseId, String actorId, String actorRole,
                                        UUID correlationId, boolean adapterEnabled) {
        SafetyCaseEntity c = requireCase(tenantId, caseId);
        if ("CLOSED".equals(c.getStatus())) {
            throw new ConflictException("Case is closed");
        }
        E2bExportAttemptEntity export = new E2bExportAttemptEntity(c.getId(), c.getTenantId());
        export.setCreatedBy(actorId);
        export.setAdapterEnabled(adapterEnabled);
        // Honest status: without a connected adapter this is "export pending", never "submitted".
        if (adapterEnabled) {
            export.setStatus("DISPATCH_PENDING");
            export.setMessage("E2B(R3)-aligned package built; queued for adapter dispatch via integration-hub.");
        } else {
            export.setStatus("EXPORT_READY");
            export.setMessage("E2B(R3)-aligned export package ready. Adapter not configured — "
                    + "use manual VigiFlow entry. This is an aligned capture package, not a certified transmission.");
        }
        export.setPayloadSummary(JsonUtil.write(buildExportSummary(c)));
        exportRepository.save(export);

        String from = c.getStatus();
        c.setStatus("EXPORT_READY");
        c.setMcazStatus(export.getMessage());
        c.touch();
        caseRepository.save(c);
        recordAction(c, "MARK_EXPORT_READY", actorId, actorRole, from, "EXPORT_READY", export.getMessage());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("case_reference", c.getCaseReference());
        payload.put("package_format", export.getPackageFormat());
        payload.put("adapter_enabled", adapterEnabled);
        payload.put("status", export.getStatus());
        events.emitCaseExportReady(c.getId(), correlationId, c.getTenantId(), c.getPodId(),
                subjectId(c), payload);
        return toCaseResponse(c);
    }

    @Transactional
    public CaseResponse close(UUID tenantId, UUID caseId, String actorId, String actorRole, String note) {
        SafetyCaseEntity c = requireCase(tenantId, caseId);
        String from = c.getStatus();
        c.setStatus("CLOSED");
        c.setClosedAt(OffsetDateTime.now());
        c.setMcazStatus(note != null ? note : "Closed");
        c.touch();
        caseRepository.save(c);
        recordAction(c, "STATUS_CHANGE", actorId, actorRole, from, "CLOSED", note);
        return toCaseResponse(c);
    }

    // ---- helpers ------------------------------------------------------------

    private SafetyCaseEntity requireCase(UUID tenantId, UUID caseId) {
        return caseRepository.findByIdAndTenantId(caseId, tenantId)
                .orElseThrow(() -> new NotFoundException("Safety case not found"));
    }

    private void recordAction(SafetyCaseEntity c, String type, String actorId, String actorRole,
                              String from, String to, String note) {
        CaseActionEntity a = new CaseActionEntity(c.getId(), c.getTenantId(), type);
        a.setActorId(actorId);
        a.setActorRole(actorRole);
        a.setFromStatus(from);
        a.setToStatus(to);
        a.setNote(note);
        actionRepository.save(a);
    }

    private static String derivePriority(String reportType, boolean serious) {
        if (serious && "AEFI".equalsIgnoreCase(reportType)) {
            return "URGENT";
        }
        return serious ? "HIGH" : "ROUTINE";
    }

    private String nextCaseReference(UUID tenantId) {
        int year = LocalDate.now().getYear();
        String prefix = "PSC-" + year + "-";
        long seq = caseRepository.countByTenantIdAndCaseReferenceStartingWith(tenantId, prefix) + 1;
        return prefix + String.format("%06d", seq);
    }

    private String subjectId(SafetyReportEntity report) {
        return report.getSubjectCpid() != null && !report.getSubjectCpid().isBlank()
                ? report.getSubjectCpid() : report.getId().toString();
    }

    private String subjectId(SafetyCaseEntity c) {
        return reportRepository.findById(c.getReportId())
                .map(this::subjectId)
                .orElse(c.getId().toString());
    }

    private Map<String, Object> buildExportSummary(SafetyCaseEntity c) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("case_reference", c.getCaseReference());
        summary.put("report_type", c.getReportType());
        summary.put("serious", c.isSerious());
        summary.put("causality", c.getCausalityAssessment());
        reportRepository.findById(c.getReportId()).ifPresent(r -> {
            summary.put("report_reference", r.getReferenceCode());
            summary.put("products", productRepository.findByReportIdOrderByCreatedAtAsc(r.getId())
                    .stream().map(p -> Map.of(
                            "name", p.getProductName(),
                            "role", p.getProductRole(),
                            "batch", p.getBatchNumber() == null ? "" : p.getBatchNumber()))
                    .toList());
        });
        return summary;
    }

    // ---- assembly -----------------------------------------------------------

    public CaseResponse toCaseResponse(SafetyCaseEntity c) {
        List<CaseResponse.TimelineEntry> timeline = actionRepository.findByCaseIdOrderByCreatedAtAsc(c.getId())
                .stream().map(a -> new CaseResponse.TimelineEntry(
                        a.getId(), a.getActionType(), a.getActorId(), a.getActorRole(),
                        a.getFromStatus(), a.getToStatus(), a.getNote(), a.getCreatedAt()))
                .toList();

        List<CaseResponse.FollowUpView> followUps = followUpRequestRepository.findByCaseIdOrderByCreatedAtAsc(c.getId())
                .stream().map(fu -> new CaseResponse.FollowUpView(
                        fu.getId(), fu.getRequestText(), fu.getChannelHint(), fu.getConversationRef(),
                        fu.getStatus(), fu.getDueDate(), fu.getCreatedAt(), fu.getRespondedAt(),
                        followUpResponseRepository.findByFollowUpRequestIdOrderByCreatedAtAsc(fu.getId())
                                .stream().map(r -> new CaseResponse.FollowUpResponseView(
                                        r.getId(), r.getResponderId(), r.getResponderKind(), r.getResponseText(),
                                        r.getAttachmentObjectId(), r.getCreatedAt()))
                                .toList()))
                .toList();

        List<CaseResponse.VigiFlowView> vigiflow = vigiFlowRepository.findByCaseIdOrderByCreatedAtAsc(c.getId())
                .stream().map(v -> new CaseResponse.VigiFlowView(
                        v.getId(), v.getEntryMode(), v.getVigiflowReferenceId(), v.getEnteredBy(),
                        v.getNote(), v.getEnteredAt()))
                .toList();

        CaseResponse.ExportView latestExport = exportRepository.findByCaseIdOrderByCreatedAtDesc(c.getId())
                .stream().findFirst()
                .map(e -> new CaseResponse.ExportView(
                        e.getId(), e.getPackageFormat(), e.getStatus(), e.getAdapterKey(),
                        e.isAdapterEnabled(), e.getMessage(), e.getCreatedAt()))
                .orElse(null);

        CaseResponse.InvestigationView investigation = investigationRepository.findByCaseId(c.getId())
                .map(i -> new CaseResponse.InvestigationView(
                        i.getId(), i.getInvestigationReference(), i.getStatus(), i.getAssignedTo(),
                        i.getPlannedDate(), i.getFinalClassification(), i.getSummary(),
                        i.getCreatedAt(), i.getUpdatedAt()))
                .orElse(null);

        var reportSummary = reportRepository.findById(c.getReportId())
                .map(r -> ReportAssembler.summary(r,
                        ReportAssembler.primaryProductName(productRepository.findByReportIdOrderByCreatedAtAsc(r.getId())),
                        null))
                .orElse(null);

        return new CaseResponse(
                c.getId(), c.getCaseReference(), c.getStatus(), c.getPriority(), c.getReportType(),
                c.isSerious(), c.getAssignedReviewerId(), c.getCausalityAssessment(), c.getMcazStatus(),
                c.getTriagedAt(), c.getClosedAt(), c.getCreatedAt(), c.getUpdatedAt(),
                reportSummary, timeline, followUps, vigiflow, latestExport, investigation);
    }

    public CaseSummary toCaseSummary(SafetyCaseEntity c) {
        var report = reportRepository.findById(c.getReportId()).orElse(null);
        String reportReference = report != null ? report.getReferenceCode() : null;
        String subjectInitials = report != null ? report.getSubjectInitials() : null;
        String primaryProduct = report != null
                ? ReportAssembler.primaryProductName(productRepository.findByReportIdOrderByCreatedAtAsc(report.getId()))
                : null;
        return new CaseSummary(
                c.getId(), c.getCaseReference(), c.getStatus(), c.getPriority(), c.getReportType(),
                c.isSerious(), c.getCausalityAssessment(), c.getAssignedReviewerId(),
                reportReference, subjectInitials, primaryProduct, c.getCreatedAt(), c.getUpdatedAt());
    }
}
