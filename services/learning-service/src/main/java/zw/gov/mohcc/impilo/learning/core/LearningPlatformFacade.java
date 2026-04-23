package zw.gov.mohcc.impilo.learning.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.api.dto.VarapiFundoCompletionPayload;
import zw.gov.mohcc.impilo.learning.config.LearningProperties;
import zw.gov.mohcc.impilo.learning.persistence.entity.ContextualLearningHintEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CpdLearningLinkEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.HelpdeskKnowledgeLinkEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningAuditEventEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningCompletionEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningOutboxEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningPathEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningProgressEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningResourceEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.MoodleWsSnapshotEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.RoleLearningRequirementEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.SubjectPathAssignmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.UserLearningProfileEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.WorkflowLearningLinkEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.ContextualLearningHintRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CpdLearningLinkRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.HelpdeskKnowledgeLinkRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningAuditEventRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningCompletionRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningOutboxRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningPathRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningProgressRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningResourceRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MoodleWsSnapshotRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.RoleLearningRequirementRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.SubjectPathAssignmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.UserLearningProfileRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.WorkflowLearningLinkRepository;

@Service
public class LearningPlatformFacade {

    public static final String SUBJECT_PROVIDER_PUBLIC_ID = "PROVIDER_PUBLIC_ID";

    private final LearningResourceRepository resourceRepository;
    private final LearningPathRepository pathRepository;
    private final RoleLearningRequirementRepository roleRequirementRepository;
    private final WorkflowLearningLinkRepository workflowLinkRepository;
    private final HelpdeskKnowledgeLinkRepository helpdeskLinkRepository;
    private final ContextualLearningHintRepository hintRepository;
    private final LearningProgressRepository progressRepository;
    private final LearningCompletionRepository completionRepository;
    private final CpdLearningLinkRepository cpdLearningLinkRepository;
    private final LearningAuditEventRepository auditRepository;
    private final LearningOutboxRepository outboxRepository;
    private final SubjectPathAssignmentRepository subjectPathAssignmentRepository;
    private final MoodleWsSnapshotRepository moodleWsSnapshotRepository;
    private final UserLearningProfileRepository userLearningProfileRepository;
    private final LearningProperties learningProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LearningPlatformFacade(
            LearningResourceRepository resourceRepository,
            LearningPathRepository pathRepository,
            RoleLearningRequirementRepository roleRequirementRepository,
            WorkflowLearningLinkRepository workflowLinkRepository,
            HelpdeskKnowledgeLinkRepository helpdeskLinkRepository,
            ContextualLearningHintRepository hintRepository,
            LearningProgressRepository progressRepository,
            LearningCompletionRepository completionRepository,
            CpdLearningLinkRepository cpdLearningLinkRepository,
            LearningAuditEventRepository auditRepository,
            LearningOutboxRepository outboxRepository,
            SubjectPathAssignmentRepository subjectPathAssignmentRepository,
            MoodleWsSnapshotRepository moodleWsSnapshotRepository,
            UserLearningProfileRepository userLearningProfileRepository,
            LearningProperties learningProperties) {
        this.resourceRepository = resourceRepository;
        this.pathRepository = pathRepository;
        this.roleRequirementRepository = roleRequirementRepository;
        this.workflowLinkRepository = workflowLinkRepository;
        this.helpdeskLinkRepository = helpdeskLinkRepository;
        this.hintRepository = hintRepository;
        this.progressRepository = progressRepository;
        this.completionRepository = completionRepository;
        this.cpdLearningLinkRepository = cpdLearningLinkRepository;
        this.auditRepository = auditRepository;
        this.outboxRepository = outboxRepository;
        this.subjectPathAssignmentRepository = subjectPathAssignmentRepository;
        this.moodleWsSnapshotRepository = moodleWsSnapshotRepository;
        this.userLearningProfileRepository = userLearningProfileRepository;
        this.learningProperties = learningProperties;
    }

    @Transactional
    public Map<String, Object> ingestVarapiFundoCompletion(VarapiFundoCompletionPayload p) {
        if (completionRepository.existsByTenantIdAndSourceTypeAndSourceRef(
                p.tenantId(), "FUNDO_WEBHOOK", p.externalRef())) {
            return Map.of("status", "duplicate", "externalRef", p.externalRef());
        }
        String code = "FUNDO_COURSE_" + sanitize(p.courseId());
        LearningResourceEntity resource =
                resourceRepository
                        .findByTenantIdAndResourceCode(p.tenantId(), code)
                        .orElseGet(() -> createEphemeralFundoResource(p.tenantId(), code, p));

        OffsetDateTime completed = OffsetDateTime.ofInstant(p.completedAt(), ZoneOffset.UTC);

        LearningCompletionEntity completion = new LearningCompletionEntity();
        completion.setTenantId(p.tenantId());
        completion.setSubjectType(SUBJECT_PROVIDER_PUBLIC_ID);
        completion.setSubjectId(p.providerPublicId());
        completion.setResourceId(resource.getId());
        completion.setCompletionDate(completed);
        completion.setSourceType("FUNDO_WEBHOOK");
        completion.setSourceRef(p.externalRef());
        completion.setVerifiedState("UNVERIFIED");
        try {
            completion.setMetadataJson(
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "varapiFundoCandidateId",
                                    p.varapiFundoCandidateId(),
                                    "councilId",
                                    p.councilId(),
                                    "creditsSuggested",
                                    p.creditsSuggested())));
        } catch (JsonProcessingException e) {
            completion.setMetadataJson("{}");
        }
        completionRepository.save(completion);

        LearningProgressEntity progress = progressRepository
                .findByTenantIdAndSubjectTypeAndSubjectIdAndResourceId(
                        p.tenantId(), SUBJECT_PROVIDER_PUBLIC_ID, p.providerPublicId(), resource.getId())
                .orElseGet(LearningProgressEntity::new);
        if (progress.getId() == null) {
            progress.setId(UUID.randomUUID());
        }
        progress.setTenantId(p.tenantId());
        progress.setSubjectType(SUBJECT_PROVIDER_PUBLIC_ID);
        progress.setSubjectId(p.providerPublicId());
        progress.setResourceId(resource.getId());
        progress.setStatus("COMPLETED");
        if (progress.getStartedAt() == null) {
            progress.setStartedAt(completed);
        }
        progress.setCompletedAt(completed);
        progress.setSourceRef(p.externalRef());
        progressRepository.save(progress);

        appendAudit(
                p.tenantId(),
                "system",
                "SERVICE",
                "learning.completion.recorded",
                "LearningCompletion",
                completion.getId().toString(),
                "OK",
                null,
                Map.of("providerPublicId", p.providerPublicId(), "resourceCode", resource.getResourceCode()));

        appendOutbox(
                "LearningCompletion",
                completion.getId().toString(),
                "learning.completion.recorded",
                Map.of(
                        "tenantId",
                        p.tenantId().toString(),
                        "resourceId",
                        resource.getId().toString(),
                        "providerPublicId",
                        p.providerPublicId(),
                        "externalRef",
                        p.externalRef()));

        appendOutbox(
                "FundoIntegration",
                p.externalRef(),
                "fundo.sync.completed",
                Map.of("candidateId", p.varapiFundoCandidateId(), "courseId", p.courseId()));

        return Map.of(
                "status",
                "recorded",
                "completionId",
                completion.getId().toString(),
                "resourceId",
                resource.getId().toString());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resolveWorkflowSurface(
            UUID tenantId,
            String appCode,
            String routeRef,
            String workflowCode,
            List<String> roleCodes,
            String subjectType,
            String subjectId) {
        List<ContextualLearningHintEntity> hints =
                hintRepository.findForAppAndRoute(tenantId, appCode, routeRef);
        List<WorkflowLearningLinkEntity> byRoute =
                workflowLinkRepository.findForAppAndRoute(tenantId, appCode, routeRef);
        List<WorkflowLearningLinkEntity> byApp =
                workflowLinkRepository.findByTenantIdAndActiveTrueAndAppCode(tenantId, appCode);
        Set<UUID> resourceIds = new LinkedHashSet<>();
        for (WorkflowLearningLinkEntity l : byRoute) {
            if (l.getResourceId() != null) {
                resourceIds.add(l.getResourceId());
            }
        }
        for (WorkflowLearningLinkEntity l : byApp) {
            if (workflowCode != null
                    && l.getWorkflowCode() != null
                    && workflowCode.equalsIgnoreCase(l.getWorkflowCode())
                    && l.getResourceId() != null) {
                resourceIds.add(l.getResourceId());
            }
        }
        for (ContextualLearningHintEntity h : hints) {
            if (h.getResourceId() != null) {
                resourceIds.add(h.getResourceId());
            }
        }
        List<RoleLearningRequirementEntity> roleReqs =
                roleCodes == null || roleCodes.isEmpty()
                        ? List.of()
                        : roleRequirementRepository.findByTenantIdAndActiveTrueAndRoleCodeIn(tenantId, roleCodes);
        for (RoleLearningRequirementEntity r : roleReqs) {
            if (r.getResourceId() != null) {
                resourceIds.add(r.getResourceId());
            }
        }
        List<LearningResourceEntity> resources =
                resourceIds.isEmpty()
                        ? List.of()
                        : resourceRepository.findByTenantIdAndIdIn(tenantId, new ArrayList<>(resourceIds));
        Map<UUID, LearningResourceEntity> byId =
                resources.stream().collect(Collectors.toMap(LearningResourceEntity::getId, r -> r));

        List<Map<String, Object>> hintViews = new ArrayList<>();
        for (ContextualLearningHintEntity h : hints) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hintType", h.getHintType());
            m.put("textSummary", h.getTextSummary());
            if (h.getResourceId() != null && byId.containsKey(h.getResourceId())) {
                m.put("resource", toResourceView(byId.get(h.getResourceId())));
            }
            hintViews.add(m);
        }
        List<Map<String, Object>> linkViews = new ArrayList<>();
        for (WorkflowLearningLinkEntity l : byRoute) {
            linkViews.add(toLinkView(l, byId));
        }
        List<Map<String, Object>> roleViews = new ArrayList<>();
        for (RoleLearningRequirementEntity r : roleReqs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roleCode", r.getRoleCode());
            m.put("requirementType", r.getRequirementType());
            if (r.getResourceId() != null && byId.containsKey(r.getResourceId())) {
                m.put("resource", toResourceView(byId.get(r.getResourceId())));
                m.put(
                        "cpd",
                        summarizeCpd(tenantId, r.getResourceId(), subjectType, subjectId, byId.get(r.getResourceId())));
            }
            roleViews.add(m);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hints", hintViews);
        body.put("workflowLinks", linkViews);
        body.put("roleRecommendations", roleViews);
        return body;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> helpdeskSurface(
            UUID tenantId, String issueTypeOrTopic, String subjectType, String subjectId) {
        List<HelpdeskKnowledgeLinkEntity> links =
                helpdeskLinkRepository.findForIssueType(tenantId, issueTypeOrTopic);
        List<Map<String, Object>> out = new ArrayList<>();
        for (HelpdeskKnowledgeLinkEntity l : links) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("relationType", l.getRelationType());
            if (l.getResourceId() != null) {
                resourceRepository
                        .findById(l.getResourceId())
                        .filter(r -> r.getTenantId().equals(tenantId))
                        .ifPresent(r -> {
                            row.put("resource", toResourceView(r));
                            row.put("cpd", summarizeCpd(tenantId, r.getId(), subjectType, subjectId, r));
                            row.put(
                                    "progress",
                                    summarizeProgress(tenantId, subjectType, subjectId, r.getId()));
                        });
            }
            if (l.getPathId() != null) {
                pathRepository
                        .findByTenantIdAndId(tenantId, l.getPathId())
                        .ifPresent(pe -> row.put("path", toPathView(pe)));
            }
            out.add(row);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchIndexHits(UUID tenantId, String q, int size) {
        List<LearningResourceEntity> rows =
                resourceRepository.searchActive(tenantId, q, PageRequest.of(0, Math.min(size, 50)));
        List<Map<String, Object>> results = new ArrayList<>();
        for (LearningResourceEntity r : rows) {
            results.add(toSearchHit(r));
        }
        return results;
    }

    @Transactional
    public void recordResourceOpened(
            UUID tenantId,
            String actorId,
            String actorType,
            UUID resourceId,
            String correlationId,
            Map<String, Object> workflowContext) {
        appendAudit(
                tenantId,
                actorId,
                actorType,
                "learning.resource.opened",
                "LearningResource",
                resourceId.toString(),
                "OK",
                correlationId,
                workflowContext == null ? Map.of() : workflowContext);
        appendOutbox(
                "LearningResource",
                resourceId.toString(),
                "learning.resource.opened",
                Map.of(
                        "tenantId",
                        tenantId.toString(),
                        "actorId",
                        actorId == null ? "" : actorId,
                        "correlationId",
                        correlationId == null ? "" : correlationId));
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getResourcePublicView(UUID tenantId, UUID resourceId) {
        return resourceRepository.findById(resourceId).filter(r -> r.getTenantId().equals(tenantId)).map(this::toResourceView);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSubjectCompletions(
            UUID tenantId, String subjectType, String subjectId, int limit) {
        int cap = Math.min(Math.max(limit, 1), 100);
        List<LearningCompletionEntity> rows =
                completionRepository.findByTenantIdAndSubjectTypeAndSubjectIdOrderByCompletionDateDesc(
                        tenantId, subjectType, subjectId, PageRequest.of(0, cap));
        List<Map<String, Object>> out = new ArrayList<>();
        for (LearningCompletionEntity c : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("completionId", c.getId().toString());
            m.put("resourceId", c.getResourceId().toString());
            m.put("completionDate", c.getCompletionDate().toString());
            m.put("sourceType", c.getSourceType());
            m.put("sourceRef", c.getSourceRef());
            m.put("verifiedState", c.getVerifiedState());
            resourceRepository
                    .findById(c.getResourceId())
                    .filter(r -> r.getTenantId().equals(tenantId))
                    .ifPresent(r -> {
                        m.put("resourceTitle", r.getTitle());
                        m.put("resourceCode", r.getResourceCode());
                        m.put("sourceTypeResource", r.getSourceType());
                    });
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> assignLearningPath(
            UUID tenantId,
            String subjectType,
            String subjectId,
            UUID pathId,
            String assignedByActorId,
            String correlationId,
            Map<String, Object> metadata) {
        LearningPathEntity path =
                pathRepository.findByTenantIdAndId(tenantId, pathId).orElseThrow(() -> new IllegalArgumentException("path_not_found"));
        if (subjectPathAssignmentRepository.existsByTenantIdAndSubjectTypeAndSubjectIdAndPathId(
                tenantId, subjectType, subjectId, pathId)) {
            return Map.of("status", "already_assigned", "pathId", pathId.toString(), "pathCode", path.getPathCode());
        }
        SubjectPathAssignmentEntity row = new SubjectPathAssignmentEntity();
        row.setTenantId(tenantId);
        row.setSubjectType(subjectType);
        row.setSubjectId(subjectId);
        row.setPathId(pathId);
        row.setAssignedByActorId(assignedByActorId);
        row.setCorrelationId(correlationId);
        try {
            row.setMetadataJson(metadata == null || metadata.isEmpty() ? null : objectMapper.writeValueAsString(metadata));
        } catch (JsonProcessingException e) {
            row.setMetadataJson("{}");
        }
        subjectPathAssignmentRepository.save(row);
        appendAudit(
                tenantId,
                assignedByActorId == null ? "" : assignedByActorId,
                "ORCHESTRATION",
                "learning.path.assigned",
                "LearningPath",
                pathId.toString(),
                "OK",
                correlationId,
                Map.of("subjectType", subjectType, "subjectId", subjectId));
        appendOutbox(
                "SubjectPathAssignment",
                row.getId().toString(),
                "learning.path.assigned",
                Map.of(
                        "tenantId",
                        tenantId.toString(),
                        "pathId",
                        pathId.toString(),
                        "subjectType",
                        subjectType,
                        "subjectId",
                        subjectId));
        return Map.of("status", "assigned", "assignmentId", row.getId().toString(), "pathCode", path.getPathCode());
    }

    @Transactional
    public Map<String, Object> registerLearningPrerequisite(
            UUID tenantId,
            String subjectType,
            String subjectId,
            String workflowCode,
            UUID resourceId,
            UUID pathId,
            String actorId,
            String correlationId,
            Map<String, Object> extra) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("subjectType", subjectType);
        ctx.put("subjectId", subjectId);
        ctx.put("workflowCode", workflowCode == null ? "" : workflowCode);
        if (resourceId != null) {
            ctx.put("resourceId", resourceId.toString());
        }
        if (pathId != null) {
            ctx.put("pathId", pathId.toString());
        }
        if (extra != null) {
            ctx.putAll(extra);
        }
        appendAudit(
                tenantId,
                actorId == null ? "" : actorId,
                "ORCHESTRATION",
                "learning.prerequisite.registered",
                "WorkflowPrerequisite",
                workflowCode == null ? "n/a" : workflowCode,
                "OK",
                correlationId,
                ctx);
        appendOutbox(
                "WorkflowPrerequisite",
                correlationId == null ? UUID.randomUUID().toString() : correlationId,
                "learning.prerequisite.registered",
                ctx);
        return Map.of("status", "recorded");
    }

    /**
     * Persists a Moodle WS JSON payload and, when {@code subjectType}/{@code subjectId} are provided, maps completed
     * activities to catalog {@link LearningResourceEntity} rows via {@code moodle_cm_id} (preferred) or course-level
     * FUNDO {@code external_ref}. Moodle {@code state}: 1 = complete, 2 = complete pass (both treated as complete);
     * 0 = incomplete; 3 = complete fail (ignored for platform completion).
     */
    @Transactional
    public Map<String, Object> ingestMoodleActivityCompletionResponse(
            UUID tenantId,
            String courseId,
            String moodleUserId,
            JsonNode moodleJson,
            String subjectType,
            String subjectId,
            String correlationId) {
        MoodleWsSnapshotEntity snap = new MoodleWsSnapshotEntity();
        snap.setTenantId(tenantId);
        snap.setWsFunction("core_completion_get_activities_completion_status");
        snap.setCourseId(courseId);
        snap.setMoodleUserId(moodleUserId);
        snap.setSubjectType(subjectType);
        snap.setSubjectId(subjectId);
        snap.setCorrelationId(correlationId);
        try {
            snap.setResponseJson(objectMapper.writeValueAsString(moodleJson));
        } catch (JsonProcessingException e) {
            snap.setResponseJson("{}");
        }

        int completionsWritten = 0;
        int progressUpdated = 0;
        List<String> notes = new ArrayList<>();

        if (subjectType != null
                && !subjectType.isBlank()
                && subjectId != null
                && !subjectId.isBlank()) {
            JsonNode statuses = moodleJson.path("statuses");
            if (statuses.isArray()) {
                for (JsonNode row : statuses) {
                    int state = moodleCompletionState(row);
                    if (!isMoodleSuccessfulCompletion(state)) {
                        continue;
                    }
                    long cmid = row.path("cmid").asLong(0L);
                    if (cmid <= 0) {
                        notes.add("skip_status_without_cmid");
                        continue;
                    }
                    Optional<LearningResourceEntity> res =
                            resourceRepository.findByTenantIdAndMoodleCmIdAndActiveTrue(tenantId, cmid);
                    if (res.isEmpty()) {
                        notes.add("no_resource_for_cmid_" + cmid);
                        continue;
                    }
                    LearningResourceEntity resource = res.get();
                    String sourceRef = courseId + ":" + cmid;
                    if (!completionRepository.existsByTenantIdAndSourceTypeAndSourceRef(
                            tenantId, "MOODLE_WS", sourceRef)) {
                        LearningCompletionEntity c = new LearningCompletionEntity();
                        c.setTenantId(tenantId);
                        c.setSubjectType(subjectType);
                        c.setSubjectId(subjectId);
                        c.setResourceId(resource.getId());
                        c.setCompletionDate(OffsetDateTime.now(ZoneOffset.UTC));
                        c.setSourceType("MOODLE_WS");
                        c.setSourceRef(sourceRef);
                        c.setVerifiedState("UNVERIFIED");
                        try {
                            c.setMetadataJson(objectMapper.writeValueAsString(Map.of("moodleUserId", moodleUserId)));
                        } catch (JsonProcessingException e) {
                            c.setMetadataJson("{}");
                        }
                        completionRepository.save(c);
                        completionsWritten++;
                    }
                    LearningProgressEntity progress = progressRepository
                            .findByTenantIdAndSubjectTypeAndSubjectIdAndResourceId(
                                    tenantId, subjectType, subjectId, resource.getId())
                            .orElseGet(LearningProgressEntity::new);
                    if (progress.getId() == null) {
                        progress.setId(UUID.randomUUID());
                    }
                    progress.setTenantId(tenantId);
                    progress.setSubjectType(subjectType);
                    progress.setSubjectId(subjectId);
                    progress.setResourceId(resource.getId());
                    progress.setStatus("COMPLETED");
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    if (progress.getStartedAt() == null) {
                        progress.setStartedAt(now);
                    }
                    progress.setCompletedAt(now);
                    progress.setSourceRef(sourceRef);
                    progressRepository.save(progress);
                    progressUpdated++;
                }
            } else {
                notes.add("no_statuses_array");
            }
        } else {
            notes.add("subject_context_absent_skip_progress");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("completionsWritten", completionsWritten);
        summary.put("progressRowsTouched", progressUpdated);
        summary.put("notes", notes);
        try {
            snap.setSyncSummaryJson(objectMapper.writeValueAsString(summary));
        } catch (JsonProcessingException e) {
            snap.setSyncSummaryJson("{}");
        }
        moodleWsSnapshotRepository.save(snap);

        appendOutbox(
                "MoodleWsSnapshot",
                snap.getId().toString(),
                "moodle.ws.completion.ingested",
                Map.of(
                        "tenantId",
                        tenantId.toString(),
                        "courseId",
                        courseId,
                        "moodleUserId",
                        moodleUserId,
                        "completionsWritten",
                        completionsWritten));

        Map<String, Object> out = new LinkedHashMap<>(summary);
        out.put("snapshotId", snap.getId().toString());
        return out;
    }

    @Transactional
    public void recordMoodleWsFailureSnapshot(
            UUID tenantId,
            String wsFunction,
            String courseId,
            String moodleUserId,
            String subjectType,
            String subjectId,
            String correlationId,
            String responseJson) {
        MoodleWsSnapshotEntity snap = new MoodleWsSnapshotEntity();
        snap.setTenantId(tenantId);
        snap.setWsFunction(wsFunction);
        snap.setCourseId(courseId);
        snap.setMoodleUserId(moodleUserId);
        snap.setSubjectType(subjectType);
        snap.setSubjectId(subjectId);
        snap.setCorrelationId(correlationId);
        snap.setResponseJson(responseJson);
        snap.setSyncSummaryJson("{\"outcome\":\"failure\"}");
        moodleWsSnapshotRepository.save(snap);
    }

    @Transactional
    public Map<String, Object> upsertUserLearningProfile(
            UUID tenantId,
            String subjectType,
            String subjectId,
            boolean patchFundoUserRef,
            String fundoUserRef,
            boolean patchMetadata,
            Map<String, Object> metadataPatch) {
        UserLearningProfileEntity e = userLearningProfileRepository
                .findByTenantIdAndSubjectTypeAndSubjectId(tenantId, subjectType, subjectId)
                .orElseGet(() -> {
                    UserLearningProfileEntity n = new UserLearningProfileEntity();
                    n.setTenantId(tenantId);
                    n.setSubjectType(subjectType);
                    n.setSubjectId(subjectId);
                    return n;
                });
        if (patchFundoUserRef) {
            e.setFundoUserRef(fundoUserRef);
        }
        if (patchMetadata && metadataPatch != null) {
            Map<String, Object> merged = new LinkedHashMap<>();
            if (e.getMetadataJson() != null && !e.getMetadataJson().isBlank()) {
                try {
                    merged.putAll(
                            objectMapper.readValue(e.getMetadataJson(), new TypeReference<Map<String, Object>>() {}));
                } catch (JsonProcessingException ignored) {
                    // leave merged empty
                }
            }
            for (Map.Entry<String, Object> en : metadataPatch.entrySet()) {
                if (en.getValue() == null) {
                    merged.remove(en.getKey());
                } else {
                    merged.put(en.getKey(), en.getValue());
                }
            }
            try {
                e.setMetadataJson(objectMapper.writeValueAsString(merged));
            } catch (JsonProcessingException ex) {
                e.setMetadataJson("{}");
            }
        }
        userLearningProfileRepository.save(e);
        appendAudit(
                tenantId,
                subjectId,
                subjectType,
                "learning.profile.fundo_ref.updated",
                "UserLearningProfile",
                e.getId().toString(),
                "OK",
                null,
                Map.of(
                        "fundoUserRef",
                        e.getFundoUserRef() == null ? "" : e.getFundoUserRef(),
                        "metadataPatched",
                        patchMetadata));
        appendOutbox(
                "UserLearningProfile",
                e.getId().toString(),
                "fundo.link.established",
                Map.of("tenantId", tenantId.toString(), "subjectType", subjectType, "subjectId", subjectId));
        return Map.of("status", "ok", "profileId", e.getId().toString());
    }

    private static int moodleCompletionState(JsonNode row) {
        if (row.has("state")) {
            return row.get("state").asInt(-1);
        }
        if (row.has("completionstate")) {
            return row.get("completionstate").asInt(-1);
        }
        return -1;
    }

    /** Moodle: 1 = complete, 2 = complete pass; 3 = complete fail (excluded). */
    private static boolean isMoodleSuccessfulCompletion(int state) {
        return state == 1 || state == 2;
    }

    private Map<String, Object> summarizeCpd(
            UUID tenantId,
            UUID resourceId,
            String subjectType,
            String subjectId,
            LearningResourceEntity resource) {
        List<CpdLearningLinkEntity> links =
                cpdLearningLinkRepository.findByTenantIdAndActiveTrueAndResourceId(tenantId, resourceId);
        boolean eligible = !links.isEmpty() && links.stream().anyMatch(CpdLearningLinkEntity::isCpdEligible);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cpdEligible", eligible);
        m.put("requiresCouncilReview", links.stream().anyMatch(CpdLearningLinkEntity::isRequiresReviewFlag));
        if (subjectType != null && subjectId != null) {
            m.put(
                    "hasRecordedCompletion",
                    completionRepository.existsByTenantIdAndSubjectTypeAndSubjectIdAndResourceId(
                            tenantId, subjectType, subjectId, resourceId));
        }
        m.put("resourceCode", resource.getResourceCode());
        return m;
    }

    private Map<String, Object> summarizeProgress(
            UUID tenantId, String subjectType, String subjectId, UUID resourceId) {
        if (subjectType == null || subjectId == null) {
            return Map.of("status", "UNKNOWN");
        }
        return progressRepository
                .findByTenantIdAndSubjectTypeAndSubjectIdAndResourceId(tenantId, subjectType, subjectId, resourceId)
                .map(p -> Map.<String, Object>of("status", p.getStatus(), "completedAt", str(p.getCompletedAt())))
                .orElse(Map.of("status", "NOT_STARTED"));
    }

    private static String str(OffsetDateTime t) {
        return t == null ? "" : t.toString();
    }

    private Map<String, Object> toSearchHit(LearningResourceEntity r) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("title", r.getTitle());
        content.put("name", r.getTitle());
        content.put("resourceCode", r.getResourceCode());
        content.put("sourceType", r.getSourceType());
        content.put("href", "/learning?focus=" + r.getId());
        content.put("experienceHref", "/learning?focus=" + r.getId());
        content.put("fundoLaunchUrl", buildHref(r));
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("id", "lrn-" + r.getId());
        hit.put("entityType", "learning_resource");
        hit.put("entityId", r.getId().toString());
        hit.put("contentJson", content);
        hit.put("tags", List.of("learning", r.getSourceType().toLowerCase()));
        String st = (r.getTitle() + " " + r.getResourceCode() + " " + coalesce(r.getDescription()) + " "
                        + coalesce(r.getWorkflowTags()))
                .trim();
        hit.put("searchableText", st);
        hit.put("indexedAt", OffsetDateTime.now().toString());
        return hit;
    }

    private Map<String, Object> toResourceView(LearningResourceEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId().toString());
        m.put("resourceCode", r.getResourceCode());
        m.put("title", r.getTitle());
        m.put("description", r.getDescription());
        m.put("sourceType", r.getSourceType());
        m.put("resourceType", r.getResourceType());
        m.put("deepLink", buildHref(r));
        if ("FUNDO".equalsIgnoreCase(r.getSourceType())) {
            m.put("fundoLaunchUrl", buildHref(r));
        }
        m.put("experienceHref", "/learning?focus=" + r.getId());
        return m;
    }

    private Map<String, Object> toPathView(LearningPathEntity p) {
        return Map.of(
                "id",
                p.getId().toString(),
                "pathCode",
                p.getPathCode(),
                "title",
                p.getTitle(),
                "pathType",
                p.getPathType());
    }

    private Map<String, Object> toLinkView(WorkflowLearningLinkEntity l, Map<UUID, LearningResourceEntity> byId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("linkType", l.getLinkType());
        m.put("workflowCode", l.getWorkflowCode());
        m.put("screenOrRouteRef", l.getScreenOrRouteRef());
        if (l.getResourceId() != null && byId.containsKey(l.getResourceId())) {
            m.put("resource", toResourceView(byId.get(l.getResourceId())));
        }
        return m;
    }

    private String buildHref(LearningResourceEntity r) {
        if ("FUNDO".equalsIgnoreCase(r.getSourceType())) {
            String base = learningProperties.getFundo().getPublicBaseUrl();
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            String idPart = r.getExternalRef() != null ? r.getExternalRef() : "";
            String courseView = base + "/course/view.php?id=" + idPart;
            String mode =
                    learningProperties.getFundo().getLaunchMode() == null
                            ? "DIRECT"
                            : learningProperties.getFundo().getLaunchMode().trim();
            String template = learningProperties.getFundo().getLaunchUrlTemplate();
            if ("TEMPLATE".equalsIgnoreCase(mode) && template != null && !template.isBlank()) {
                return applyFundoLaunchTemplate(template, courseView, base);
            }
            if ("WANTSURL".equalsIgnoreCase(mode) || learningProperties.getFundo().isWantsurlLoginEnabled()) {
                String login = learningProperties.getFundo().getLoginPath();
                if (!login.startsWith("/")) {
                    login = "/" + login;
                }
                String wants = URLEncoder.encode(courseView, StandardCharsets.UTF_8);
                return base + login + "?wantsurl=" + wants;
            }
            return courseView;
        }
        if (r.getMetadataJson() != null && !r.getMetadataJson().isBlank()) {
            try {
                JsonNode n = objectMapper.readTree(r.getMetadataJson());
                if (n.hasNonNull("href")) {
                    return n.get("href").asText();
                }
            } catch (JsonProcessingException ignored) {
                // fall through
            }
        }
        return "/learning?focus=" + r.getId();
    }

    private String applyFundoLaunchTemplate(String template, String courseViewUrl, String baseTrimmed) {
        String encoded = URLEncoder.encode(courseViewUrl, StandardCharsets.UTF_8);
        String pathQuery = courseViewPathAndQuery(courseViewUrl);
        return template
                .replace("{courseViewUrl}", courseViewUrl)
                .replace("{encodedCourseViewUrl}", encoded)
                .replace("{courseViewPath}", pathQuery);
    }

    private static String courseViewPathAndQuery(String courseViewUrl) {
        try {
            URI u = URI.create(courseViewUrl);
            String q = u.getRawQuery();
            return u.getRawPath() + (q != null && !q.isEmpty() ? "?" + q : "");
        } catch (Exception e) {
            return "/course/view.php";
        }
    }

    private LearningResourceEntity createEphemeralFundoResource(
            UUID tenantId, String resourceCode, VarapiFundoCompletionPayload p) {
        LearningResourceEntity e = new LearningResourceEntity();
        e.setTenantId(tenantId);
        e.setResourceCode(resourceCode);
        e.setSourceType("FUNDO");
        e.setTitle(p.courseName() != null ? p.courseName() : "Fundo course " + p.courseId());
        e.setDescription("Auto-registered from Fundo completion ingest.");
        e.setResourceType("COURSE");
        e.setExternalRef(p.courseId());
        e.setAppCode("experience");
        e.setActive(true);
        e.setMetadataJson("{\"autoCreated\":true}");
        return resourceRepository.save(e);
    }

    private static String sanitize(String courseId) {
        return courseId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static String coalesce(String s) {
        return s == null ? "" : s;
    }

    private void appendAudit(
            UUID tenantId,
            String actorId,
            String actorType,
            String action,
            String targetType,
            String targetId,
            String outcome,
            String correlationId,
            Map<String, Object> workflowContext) {
        LearningAuditEventEntity e = new LearningAuditEventEntity();
        e.setTenantId(tenantId);
        e.setActorId(actorId);
        e.setActorType(actorType);
        e.setAction(action);
        e.setTargetType(targetType);
        e.setTargetId(targetId);
        e.setOutcome(outcome);
        e.setCorrelationId(correlationId);
        try {
            e.setWorkflowContextJson(objectMapper.writeValueAsString(workflowContext));
        } catch (JsonProcessingException ex) {
            e.setWorkflowContextJson("{}");
        }
        auditRepository.save(e);
    }

    private void appendOutbox(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        LearningOutboxEntity e = new LearningOutboxEntity();
        e.setAggregateType(aggregateType);
        e.setAggregateId(aggregateId);
        e.setEventType(eventType);
        try {
            e.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            e.setPayloadJson("{}");
        }
        outboxRepository.save(e);
    }
}
