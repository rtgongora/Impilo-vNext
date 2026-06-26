package zw.gov.mohcc.impilo.rito.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.domain.CaseClassification;
import zw.gov.mohcc.impilo.rito.domain.CaseLifecycle;
import zw.gov.mohcc.impilo.rito.events.RitoEventEmitter;
import zw.gov.mohcc.impilo.rito.persistence.entity.*;
import zw.gov.mohcc.impilo.rito.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The owning service for the Rito case aggregate. Implements the operating cycle
 * Listen → Classify → Triage → Assign → Investigate → Act → Verify → Close → Learn.
 *
 * <p>Hard rules enforced here:
 * <ul>
 *   <li>AI/system actors may suggest severity/routing but may NEVER resolve or close a
 *       CRITICAL or sensitive case — that requires a human decision-maker.</li>
 *   <li>Every meaningful action writes a {@code rit_case_event} and emits an outbox event.</li>
 *   <li>Status changes are validated against {@link CaseLifecycle}.</li>
 * </ul>
 */
@Service
public class CaseService {

    private static final java.util.Set<String> NON_HUMAN_ACTORS =
            java.util.Set.of("SYSTEM", "AI", "RULES", "RULE", "BOT");

    private final CaseRepository caseRepository;
    private final CasePartyRepository partyRepository;
    private final CaseLinkRepository linkRepository;
    private final CaseEventRepository eventRepository;
    private final CaseAssignmentRepository assignmentRepository;
    private final CaseMessageRepository messageRepository;
    private final CaseAttachmentRepository attachmentRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final ReferenceGenerator referenceGenerator;
    private final RitoEventEmitter emitter;
    private final NotificationGateway notifications;

    public CaseService(CaseRepository caseRepository, CasePartyRepository partyRepository,
                       CaseLinkRepository linkRepository, CaseEventRepository eventRepository,
                       CaseAssignmentRepository assignmentRepository, CaseMessageRepository messageRepository,
                       CaseAttachmentRepository attachmentRepository, SlaPolicyRepository slaPolicyRepository,
                       ReferenceGenerator referenceGenerator, RitoEventEmitter emitter,
                       NotificationGateway notifications) {
        this.caseRepository = caseRepository;
        this.partyRepository = partyRepository;
        this.linkRepository = linkRepository;
        this.eventRepository = eventRepository;
        this.assignmentRepository = assignmentRepository;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.referenceGenerator = referenceGenerator;
        this.emitter = emitter;
        this.notifications = notifications;
    }

    /** Command carrying the fields needed to open a case (front door, provider, system, survey). */
    public record NewCase(
            String caseType, String title, String description, String severity, String source,
            String category, boolean anonymous, UUID facilityId, UUID districtId, UUID provinceId,
            UUID programmeId, String reporterActorId, String reporterActorType, String subjectHealthId,
            boolean asDraft, Map<String, Object> metadata) {
    }

    @Transactional
    public CaseEntity createCase(UUID tenantId, NewCase cmd) {
        String caseType = require(cmd.caseType(), "caseType");
        if (!CaseClassification.CASE_TYPES.contains(caseType)) {
            throw new IllegalArgumentException("Unknown caseType: " + caseType);
        }
        String severity = normaliseSeverity(cmd.severity());
        String source = cmd.source() != null ? cmd.source() : "WEB_PORTAL";
        if (!CaseClassification.SOURCES.contains(source)) {
            throw new IllegalArgumentException("Unknown source: " + source);
        }

        CaseEntity c = new CaseEntity();
        c.setTenantId(tenantId);
        c.setCaseReference(referenceGenerator.generate("RITO-C",
                ref -> caseRepository.existsByTenantIdAndCaseReference(tenantId, ref)));
        c.setCaseType(caseType);
        c.setPillar(CaseClassification.pillarFor(caseType));
        c.setCategory(cmd.category());
        c.setTitle(cmd.title());
        c.setDescription(cmd.description());
        c.setSeverity(severity);
        c.setSource(source);
        c.setAnonymous(cmd.anonymous());
        c.setSensitive(CaseClassification.isSensitive(caseType, cmd.category()));
        c.setFacilityId(cmd.facilityId());
        c.setDistrictId(cmd.districtId());
        c.setProvinceId(cmd.provinceId());
        c.setProgrammeId(cmd.programmeId());
        if (!cmd.anonymous()) {
            c.setReporterActorId(cmd.reporterActorId());
            c.setReporterActorType(cmd.reporterActorType());
        }
        c.setSubjectHealthId(cmd.subjectHealthId());
        c.setStatus(cmd.asDraft() ? CaseLifecycle.DRAFT : CaseLifecycle.SUBMITTED);
        c.setCreatedBy(cmd.reporterActorId());
        applySla(c, tenantId, caseType, severity);
        c.setMetadataJson(JsonUtil.toJson(cmd.metadata()));
        caseRepository.save(c);

        if (!cmd.anonymous() && cmd.reporterActorId() != null) {
            addParty(tenantId, c.getId(), reporterRole(cmd.reporterActorType()),
                    cmd.reporterActorId(), cmd.reporterActorType(), null, c.getSensitive());
        }

        recordEvent(c, CaseLifecycle.SUBMITTED, null, c.getStatus(), cmd.reporterActorId(),
                cmd.reporterActorType(), "Case opened via " + source, null);

        if (!cmd.asDraft()) {
            emitCaseEvent(c, "rito.case.submitted");
            emitTypeSpecificSubmission(c);
            notifications.requestNotification(tenantId, c.getId(), "rito.case.acknowledged.client",
                    "CLIENT", Map.of("caseReference", c.getCaseReference()));
        }
        return c;
    }

    @Transactional(readOnly = true)
    public CaseEntity get(UUID tenantId, UUID id) {
        return caseRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Case not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<CaseEntity> list(UUID tenantId, String status, String caseType, UUID facilityId, String assignedTo) {
        if (status != null) {
            return caseRepository.findByTenantIdAndStatus(tenantId, status);
        }
        if (caseType != null) {
            return caseRepository.findByTenantIdAndCaseType(tenantId, caseType);
        }
        if (facilityId != null) {
            return caseRepository.findByTenantIdAndFacilityId(tenantId, facilityId);
        }
        if (assignedTo != null) {
            return caseRepository.findByTenantIdAndAssignedToActorId(tenantId, assignedTo);
        }
        return caseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<CaseEventEntity> timeline(UUID tenantId, UUID caseId) {
        get(tenantId, caseId);
        return eventRepository.findByCaseIdOrderByOccurredAtAsc(caseId);
    }

    @Transactional(readOnly = true)
    public List<CasePartyEntity> parties(UUID tenantId, UUID caseId) {
        get(tenantId, caseId);
        return partyRepository.findByCaseId(caseId);
    }

    @Transactional(readOnly = true)
    public List<CaseLinkEntity> links(UUID tenantId, UUID caseId) {
        get(tenantId, caseId);
        return linkRepository.findByCaseId(caseId);
    }

    @Transactional(readOnly = true)
    public List<CaseMessageEntity> messages(UUID tenantId, UUID caseId) {
        get(tenantId, caseId);
        return messageRepository.findByCaseIdOrderByCreatedAtAsc(caseId);
    }

    @Transactional
    public CaseEntity acknowledge(UUID tenantId, UUID id, String actorId, String actorType) {
        CaseEntity c = get(tenantId, id);
        applyStatus(c, CaseLifecycle.ACKNOWLEDGED, actorId, actorType, "Acknowledged");
        c.setAcknowledgedAt(OffsetDateTime.now());
        caseRepository.save(c);
        emitCaseEvent(c, "rito.case.acknowledged");
        return c;
    }

    @Transactional
    public CaseEntity triage(UUID tenantId, UUID id, String severity, String category,
                             String aiSuggestedSeverity, String aiSuggestedRouting, String aiRationale,
                             String actorId, String actorType) {
        CaseEntity c = get(tenantId, id);
        if (severity != null) {
            c.setSeverity(normaliseSeverity(severity));
        }
        if (category != null) {
            c.setCategory(category);
            c.setSensitive(CaseClassification.isSensitive(c.getCaseType(), category));
        }
        // AI suggestions are advisory only — recorded, never auto-applied to status.
        c.setAiSuggestedSeverity(aiSuggestedSeverity);
        c.setAiSuggestedRouting(aiSuggestedRouting);
        c.setAiRationale(aiRationale);
        applyStatus(c, CaseLifecycle.TRIAGED, actorId, actorType, "Triaged");
        c.setTriagedAt(OffsetDateTime.now());
        applySla(c, tenantId, c.getCaseType(), c.getSeverity());
        caseRepository.save(c);
        emitCaseEvent(c, "rito.case.triaged");
        return c;
    }

    @Transactional
    public CaseEntity assign(UUID tenantId, UUID id, String assigneeActorId, String assigneeActorType,
                             String assigneeRole, String byActorId, String byActorType, String reason) {
        CaseEntity c = get(tenantId, id);
        require(assigneeActorId, "assigneeActorId");
        for (CaseAssignmentEntity active : assignmentRepository.findByCaseIdAndActiveTrue(id)) {
            active.setActive(false);
            active.setUnassignedAt(OffsetDateTime.now());
            assignmentRepository.save(active);
        }
        CaseAssignmentEntity a = new CaseAssignmentEntity();
        a.setCaseId(id);
        a.setTenantId(tenantId);
        a.setAssigneeActorId(assigneeActorId);
        a.setAssigneeActorType(assigneeActorType);
        a.setAssigneeRole(assigneeRole);
        a.setAssignedBy(byActorId);
        a.setReason(reason);
        a.setActive(true);
        assignmentRepository.save(a);

        c.setAssignedToActorId(assigneeActorId);
        c.setAssignedToActorType(assigneeActorType);
        if (CaseLifecycle.canTransition(c.getStatus(), CaseLifecycle.ASSIGNED)
                && !CaseLifecycle.TERMINAL.contains(c.getStatus())) {
            applyStatus(c, CaseLifecycle.ASSIGNED, byActorId, byActorType, "Assigned to " + assigneeRole);
        } else {
            recordEvent(c, "ASSIGNED", c.getStatus(), c.getStatus(), byActorId, byActorType,
                    "Reassigned to " + assigneeActorId, null);
        }
        caseRepository.save(c);
        emitCaseEvent(c, "rito.case.assigned");
        notifications.requestNotification(tenantId, id, "rito.case.assigned.owner", assigneeRole,
                Map.of("caseReference", c.getCaseReference()));
        return c;
    }

    /** Generic guarded transition for the working/investigation/waiting states. */
    @Transactional
    public CaseEntity transition(UUID tenantId, UUID id, String toStatus, String note,
                                 String actorId, String actorType) {
        CaseEntity c = get(tenantId, id);
        if (!CaseLifecycle.isKnown(toStatus)) {
            throw new IllegalArgumentException("Unknown status: " + toStatus);
        }
        applyStatus(c, toStatus, actorId, actorType, note);
        caseRepository.save(c);
        return c;
    }

    @Transactional
    public CaseEntity escalate(UUID tenantId, UUID id, String toLevel, String reason,
                               String actorId, String actorType) {
        CaseEntity c = get(tenantId, id);
        applyStatus(c, CaseLifecycle.ESCALATED, actorId, actorType,
                "Escalated to " + toLevel + (reason != null ? ": " + reason : ""));
        caseRepository.save(c);
        Map<String, Object> payload = baseCasePayload(c);
        payload.put("escalationLevel", toLevel);
        payload.put("reason", reason);
        emitter.emit("CASE", c.getId().toString(), "rito.case.escalated", "CASE",
                c.getId().toString(), payload, tenantId);
        notifications.requestNotification(tenantId, id, "rito.case.escalated.supervisor", "SUPERVISOR",
                Map.of("caseReference", c.getCaseReference(), "level", toLevel == null ? "" : toLevel));
        return c;
    }

    @Transactional
    public CaseEntity resolve(UUID tenantId, UUID id, String resolutionSummary, String outcome,
                              String actorId, String actorType) {
        CaseEntity c = get(tenantId, id);
        guardHumanDecision(c, actorType, "resolve");
        applyStatus(c, CaseLifecycle.RESOLVED, actorId, actorType, "Resolved");
        c.setResolutionSummary(resolutionSummary);
        c.setClosureOutcome(outcome);
        c.setResolvedAt(OffsetDateTime.now());
        caseRepository.save(c);
        emitCaseEvent(c, "rito.case.resolved");
        return c;
    }

    @Transactional
    public CaseEntity close(UUID tenantId, UUID id, Integer satisfactionScore,
                            String actorId, String actorType) {
        CaseEntity c = get(tenantId, id);
        guardHumanDecision(c, actorType, "close");
        applyStatus(c, CaseLifecycle.CLOSED, actorId, actorType, "Closed");
        if (satisfactionScore != null) {
            if (satisfactionScore < 1 || satisfactionScore > 5) {
                throw new IllegalArgumentException("satisfactionScore must be 1..5");
            }
            c.setSatisfactionScore(satisfactionScore);
        }
        c.setClosedAt(OffsetDateTime.now());
        caseRepository.save(c);
        emitCaseEvent(c, "rito.case.closed");
        return c;
    }

    @Transactional
    public CaseEntity reopen(UUID tenantId, UUID id, String reason, String actorId, String actorType) {
        CaseEntity c = get(tenantId, id);
        applyStatus(c, CaseLifecycle.REOPENED, actorId, actorType,
                "Reopened" + (reason != null ? ": " + reason : ""));
        c.setResolvedAt(null);
        c.setClosedAt(null);
        caseRepository.save(c);
        emitCaseEvent(c, "rito.case.reopened");
        return c;
    }

    @Transactional
    public CaseEntity cancel(UUID tenantId, UUID id, String reason, String actorId, String actorType) {
        CaseEntity c = get(tenantId, id);
        applyStatus(c, CaseLifecycle.CANCELLED, actorId, actorType,
                "Cancelled" + (reason != null ? ": " + reason : ""));
        caseRepository.save(c);
        return c;
    }

    @Transactional
    public CaseMessageEntity addMessage(UUID tenantId, UUID id, String direction, String channel,
                                        String authorActorId, String authorActorType, String visibility,
                                        String body) {
        CaseEntity c = get(tenantId, id);
        CaseMessageEntity m = new CaseMessageEntity();
        m.setCaseId(c.getId());
        m.setTenantId(tenantId);
        m.setDirection(direction != null ? direction : "INTERNAL");
        m.setChannel(channel);
        m.setAuthorActorId(authorActorId);
        m.setAuthorActorType(authorActorType);
        m.setVisibility(visibility != null ? visibility : "INTERNAL");
        m.setBody(require(body, "body"));
        messageRepository.save(m);
        recordEvent(c, "MESSAGE_ADDED", c.getStatus(), c.getStatus(), authorActorId, authorActorType,
                null, Map.of("direction", m.getDirection(), "visibility", m.getVisibility()));
        return m;
    }

    @Transactional
    public CasePartyEntity addParty(UUID tenantId, UUID caseId, String role, String actorId,
                                    String actorType, String displayName, boolean protect) {
        CasePartyEntity p = new CasePartyEntity();
        p.setCaseId(caseId);
        p.setTenantId(tenantId);
        p.setPartyRole(require(role, "partyRole"));
        p.setActorId(actorId);
        p.setActorType(actorType);
        p.setDisplayName(displayName);
        p.setIdentityProtected(protect);
        return partyRepository.save(p);
    }

    @Transactional
    public CaseLinkEntity addLink(UUID tenantId, UUID caseId, String linkType, String targetRef,
                                  String targetSystem, String note) {
        CaseEntity c = get(tenantId, caseId);
        if (!CaseClassification.LINK_TYPES.contains(linkType)) {
            throw new IllegalArgumentException("Unknown linkType: " + linkType);
        }
        CaseLinkEntity l = new CaseLinkEntity();
        l.setCaseId(c.getId());
        l.setTenantId(tenantId);
        l.setLinkType(linkType);
        l.setTargetRef(require(targetRef, "targetRef"));
        l.setTargetSystem(targetSystem);
        l.setNote(note);
        return linkRepository.save(l);
    }

    @Transactional
    public CaseAttachmentEntity addAttachment(UUID tenantId, UUID caseId, String documentRef,
                                              String fileName, String contentType, Long sizeBytes,
                                              String uploadedBy) {
        CaseEntity c = get(tenantId, caseId);
        CaseAttachmentEntity a = new CaseAttachmentEntity();
        a.setCaseId(c.getId());
        a.setTenantId(tenantId);
        a.setDocumentRef(require(documentRef, "documentRef"));
        a.setFileName(fileName);
        a.setContentType(contentType);
        a.setSizeBytes(sizeBytes);
        a.setUploadedBy(uploadedBy);
        return attachmentRepository.save(a);
    }

    // ---- internal helpers ----

    private void guardHumanDecision(CaseEntity c, String actorType, String action) {
        boolean criticalOrSensitive = Boolean.TRUE.equals(c.getSensitive())
                || "CRITICAL".equals(c.getSeverity());
        if (criticalOrSensitive && actorType != null
                && NON_HUMAN_ACTORS.contains(actorType.toUpperCase())) {
            throw new IllegalStateException(
                    "A CRITICAL or sensitive case cannot be " + action
                            + "d by an automated actor — a human decision-maker is required.");
        }
    }

    private void applyStatus(CaseEntity c, String toStatus, String actorId, String actorType, String note) {
        String from = c.getStatus();
        if (!CaseLifecycle.canTransition(from, toStatus)) {
            throw new IllegalStateException("Illegal transition " + from + " → " + toStatus);
        }
        c.setStatus(toStatus);
        recordEvent(c, statusEventType(toStatus), from, toStatus, actorId, actorType, note, null);
    }

    private String statusEventType(String toStatus) {
        return switch (toStatus) {
            case CaseLifecycle.ACKNOWLEDGED -> "ACKNOWLEDGED";
            case CaseLifecycle.TRIAGED -> "TRIAGED";
            case CaseLifecycle.ASSIGNED -> "ASSIGNED";
            case CaseLifecycle.ESCALATED -> "ESCALATED";
            case CaseLifecycle.RESOLVED -> "RESOLVED";
            case CaseLifecycle.CLOSED -> "CLOSED";
            case CaseLifecycle.REOPENED -> "REOPENED";
            case CaseLifecycle.CANCELLED -> "CANCELLED";
            default -> "STATUS_CHANGED";
        };
    }

    private void recordEvent(CaseEntity c, String eventType, String fromStatus, String toStatus,
                             String actorId, String actorType, String note, Map<String, Object> detail) {
        CaseEventEntity e = new CaseEventEntity();
        e.setCaseId(c.getId());
        e.setTenantId(c.getTenantId());
        e.setEventType(eventType);
        e.setFromStatus(fromStatus);
        e.setToStatus(toStatus);
        e.setActorId(actorId);
        e.setActorType(actorType);
        e.setNote(note);
        if (detail != null) {
            e.setDetailJson(JsonUtil.toJson(detail));
        }
        eventRepository.save(e);
    }

    private void applySla(CaseEntity c, UUID tenantId, String caseType, String severity) {
        SlaPolicyEntity best = null;
        int bestScore = -1;
        for (SlaPolicyEntity p : slaPolicyRepository.findByTenantIdAndActiveTrue(tenantId)) {
            int score = 0;
            if (p.getAppliesToCaseType() != null) {
                if (!p.getAppliesToCaseType().equals(caseType)) {
                    continue;
                }
                score += 2;
            }
            if (p.getAppliesToSeverity() != null) {
                if (!p.getAppliesToSeverity().equals(severity)) {
                    continue;
                }
                score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        if (best != null) {
            c.setSlaPolicyId(best.getId());
            if (best.getResolveWithinMinutes() != null) {
                c.setDueAt(OffsetDateTime.now().plusMinutes(best.getResolveWithinMinutes()));
            }
        }
    }

    private void emitCaseEvent(CaseEntity c, String eventType) {
        emitter.emit("CASE", c.getId().toString(), eventType, "CASE", c.getId().toString(),
                baseCasePayload(c), c.getTenantId());
    }

    private void emitTypeSpecificSubmission(CaseEntity c) {
        UUID t = c.getTenantId();
        Map<String, Object> payload = baseCasePayload(c);
        switch (c.getCaseType()) {
            case CaseClassification.COMPLAINT ->
                    emitter.emit("CASE", c.getId().toString(), "rito.complaint.submitted", "CASE", c.getId().toString(), payload, t);
            case CaseClassification.COMPLIMENT ->
                    emitter.emit("CASE", c.getId().toString(), "rito.compliment.submitted", "CASE", c.getId().toString(), payload, t);
            case CaseClassification.SUGGESTION ->
                    emitter.emit("CASE", c.getId().toString(), "rito.suggestion.submitted", "CASE", c.getId().toString(), payload, t);
            case CaseClassification.SATISFACTION_SURVEY ->
                    emitter.emit("CASE", c.getId().toString(), "rito.feedback.submitted", "CASE", c.getId().toString(), payload, t);
            case CaseClassification.SAFETY_INCIDENT ->
                    emitSafety(c, "rito.safety.incident.reported");
            case CaseClassification.NEAR_MISS ->
                    emitSafety(c, "rito.safety.near_miss.reported");
            case CaseClassification.ADVERSE_EVENT ->
                    emitSafety(c, "rito.safety.adverse_event.reported");
            default -> { /* QUALITY_FINDING / SYSTEM_QUALITY_SIGNAL / AUDIT_FINDING / SAFETY_CONCERN — case.submitted suffices */ }
        }
    }

    private void emitSafety(CaseEntity c, String eventType) {
        UUID t = c.getTenantId();
        emitter.emit("SAFETY_INCIDENT", c.getId().toString(), eventType, "CASE", c.getId().toString(),
                baseCasePayload(c), t);
        if ("CRITICAL".equals(c.getSeverity())) {
            emitter.emit("SAFETY_INCIDENT", c.getId().toString(), "rito.safety.critical_alert.created",
                    "CASE", c.getId().toString(), baseCasePayload(c), t);
            notifications.requestNotification(t, c.getId(), "rito.safety.critical_alert", "FACILITY_SAFETY_LEAD",
                    Map.of("caseReference", c.getCaseReference(), "severity", "CRITICAL"));
        }
    }

    private Map<String, Object> baseCasePayload(CaseEntity c) {
        Map<String, Object> m = new HashMap<>();
        m.put("caseId", c.getId().toString());
        m.put("caseReference", c.getCaseReference());
        m.put("caseType", c.getCaseType());
        m.put("pillar", c.getPillar());
        m.put("severity", c.getSeverity());
        m.put("status", c.getStatus());
        m.put("facilityId", c.getFacilityId() != null ? c.getFacilityId().toString() : null);
        m.put("sensitive", c.getSensitive());
        return m;
    }

    private static String reporterRole(String actorType) {
        if (actorType == null) {
            return "CLIENT";
        }
        return switch (actorType.toUpperCase()) {
            case "PROVIDER", "STAFF", "CLINICIAN" -> "PROVIDER";
            case "AGENT", "KHULUMA_AGENT" -> "AGENT";
            case "CAREGIVER" -> "CAREGIVER";
            default -> "CLIENT";
        };
    }

    private static String normaliseSeverity(String severity) {
        if (severity == null) {
            return "MODERATE";
        }
        String s = severity.toUpperCase();
        if (!CaseClassification.SEVERITIES.contains(s)) {
            throw new IllegalArgumentException("Unknown severity: " + severity);
        }
        return s;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }
}
