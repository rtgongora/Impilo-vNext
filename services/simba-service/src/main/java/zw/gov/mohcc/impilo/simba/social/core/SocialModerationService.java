package zw.gov.mohcc.impilo.simba.social.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.core.CareLinkageService;
import zw.gov.mohcc.impilo.simba.core.SimbaEventEmitter;
import zw.gov.mohcc.impilo.simba.persistence.entity.CareLinkageEntity;
import zw.gov.mohcc.impilo.simba.social.entity.ContentReportEntity;
import zw.gov.mohcc.impilo.simba.social.entity.ModerationActionEntity;
import zw.gov.mohcc.impilo.simba.social.repository.ContentReportRepository;
import zw.gov.mohcc.impilo.simba.social.repository.ModerationActionRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Social moderation + SAFETY escalation. Report intake runs a state machine; but crucially, reports
 * with a SAFETY reason (SELF_HARM / ABUSE / safeguarding) are NEVER handled purely socially — they
 * build a {@link CareLinkageEntity} and route it via {@link CareLinkageService} (persisted PCT
 * escalation), stamping {@code care_linkage_id} on the report. This reuses the same care-linkage seam
 * as Part-1 risk scoring.
 */
@Service
public class SocialModerationService {

    /** Reasons that always escalate to care, not just moderation. */
    static final Set<String> SAFETY_REASONS = Set.of("SELF_HARM", "ABUSE");

    private final ContentReportRepository reportRepository;
    private final ModerationActionRepository actionRepository;
    private final CareLinkageService careLinkage;
    private final SimbaEventEmitter events;

    public SocialModerationService(ContentReportRepository reportRepository,
                                   ModerationActionRepository actionRepository,
                                   CareLinkageService careLinkage,
                                   SimbaEventEmitter events) {
        this.reportRepository = reportRepository;
        this.actionRepository = actionRepository;
        this.careLinkage = careLinkage;
        this.events = events;
    }

    /** True when the reason must escalate to a care linkage (safety), not just moderation. */
    public static boolean isSafetyReason(String reason) {
        return reason != null && SAFETY_REASONS.contains(reason.toUpperCase());
    }

    @Transactional
    public ContentReportEntity report(UUID tenantId, String reporterCpid, String subjectType,
                                      UUID subjectId, String subjectOwnerCpid, String reason,
                                      String detail) {
        ContentReportEntity r = new ContentReportEntity();
        r.setTenantId(tenantId);
        r.setReporterCpid(reporterCpid);
        r.setSubjectType(subjectType);
        r.setSubjectId(subjectId);
        r.setSubjectOwnerCpid(subjectOwnerCpid);
        r.setReason(reason == null ? "ABUSE" : reason.toUpperCase());
        r.setDetail(detail);

        // SAFETY: self-harm / abuse / safeguarding builds a care linkage — never handled socially.
        if (isSafetyReason(r.getReason()) && subjectOwnerCpid != null && !subjectOwnerCpid.isBlank()) {
            CareLinkageEntity linkage = new CareLinkageEntity();
            linkage.setTenantId(tenantId);
            linkage.setPersonCpid(subjectOwnerCpid);
            linkage.setTrigger("RED_FLAG");
            linkage.setSeverity("SELF_HARM".equals(r.getReason()) ? "EMERGENCY" : "URGENT");
            linkage.setReason("Social safety report: " + r.getReason());
            linkage.setTargetOwner("PCT");
            linkage.setCreatedBy("SIMBA_SOCIAL_MODERATION");
            CareLinkageEntity routed = careLinkage.route(linkage);
            r.setCareLinkageId(routed.getLinkageId());
            r.setStatus("TRIAGED");
        }
        r = reportRepository.save(r);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("report_id", r.getReportId().toString());
        payload.put("reason", r.getReason());
        payload.put("subject_type", subjectType);
        payload.put("subject_id", subjectId.toString());
        payload.put("care_linkage_id", r.getCareLinkageId() == null ? null : r.getCareLinkageId().toString());
        events.emit(tenantId, "WELLNESS_SOCIAL_REPORT", r.getReportId().toString(),
                "impilo.simba.wellness.social.report.created.v1", reporterCpid,
                "simba:social-report:" + r.getReportId(), payload);
        return r;
    }

    @Transactional(readOnly = true)
    public List<ContentReportEntity> inbox(UUID tenantId, String status) {
        return reportRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId,
                status == null ? "OPEN" : status.toUpperCase());
    }

    @Transactional(readOnly = true)
    public long backlogCount(UUID tenantId) {
        return reportRepository.countByTenantIdAndStatus(tenantId, "OPEN");
    }

    /** Records a moderation action + advances the report. Returns the persisted action. */
    @Transactional
    public ModerationActionEntity act(UUID tenantId, UUID reportId, String moderatorCpid,
                                      String actionState, String rationale) {
        ContentReportEntity report = reportRepository.findByReportIdAndTenantId(reportId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));

        ModerationActionEntity action = new ModerationActionEntity();
        action.setTenantId(tenantId);
        action.setReportId(reportId);
        action.setSubjectType(report.getSubjectType());
        action.setSubjectId(report.getSubjectId());
        action.setModeratorCpid(moderatorCpid);
        action.setActionState(actionState);
        action.setRationale(rationale);
        action = actionRepository.save(action);

        report.setStatus(switch (actionState) {
            case "REMOVED", "HIDDEN", "FLAGGED", "ESCALATED" -> "ACTIONED";
            case "REJECTED", "RESTORED", "VISIBLE" -> "DISMISSED";
            default -> "TRIAGED";
        });
        reportRepository.save(report);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("report_id", reportId.toString());
        payload.put("action_state", actionState);
        payload.put("subject_type", report.getSubjectType());
        payload.put("subject_id", report.getSubjectId().toString());
        events.emit(tenantId, "WELLNESS_SOCIAL_MODERATION", action.getActionId().toString(),
                "impilo.simba.wellness.social.moderation.action.v1", moderatorCpid,
                "simba:social-mod:" + action.getActionId(), payload);
        return action;
    }
}
