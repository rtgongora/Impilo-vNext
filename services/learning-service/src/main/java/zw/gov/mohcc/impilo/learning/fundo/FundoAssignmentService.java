package zw.gov.mohcc.impilo.learning.fundo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssignmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssignmentSubmissionEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssignmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssignmentSubmissionRepository;

/**
 * Assignments / tasks + submissions + marking (A3). Distinct from quiz assessments:
 * free deliverables marked by a human. The marking workflow mirrors the assessment
 * manual-review pattern (PENDING_MARK queue → MARKED with score/feedback/rubric).
 */
@Service
public class FundoAssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final FundoOutboxAppender outbox;
    private final ObjectMapper objectMapper;

    public FundoAssignmentService(
            AssignmentRepository assignmentRepository,
            AssignmentSubmissionRepository submissionRepository,
            FundoOutboxAppender outbox,
            ObjectMapper objectMapper) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> createAssignment(UUID tenantId, String actorId, Map<String, Object> body) {
        AssignmentEntity a = new AssignmentEntity();
        a.setTenantId(tenantId);
        a.setTitle(reqStr(body, "title"));
        a.setInstructions(nullable(body, "instructions"));
        a.setCourseId(uuidOrNull(body.get("courseId")));
        a.setCohortId(uuidOrNull(body.get("cohortId")));
        a.setModuleId(uuidOrNull(body.get("moduleId")));
        if (body.get("maxScore") != null) {
            a.setMaxScore(Integer.valueOf(body.get("maxScore").toString()));
        }
        a.setRubricJson(writeJson(body.get("rubric")));
        a.setAllowLate(Boolean.parseBoolean(String.valueOf(body.getOrDefault("allowLate", true))));
        a.setStatus(str(body, "status", "OPEN"));
        a.setCreatedBy(actorId);
        assignmentRepository.save(a);
        return assignmentView(a);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listAssignments(UUID tenantId, String courseId, String cohortId, int limit) {
        List<AssignmentEntity> rows;
        PageRequest page = PageRequest.of(0, limit);
        if (courseId != null && !courseId.isBlank()) {
            rows = assignmentRepository.findByTenantIdAndCourseIdOrderByCreatedAtDesc(tenantId, UUID.fromString(courseId), page);
        } else if (cohortId != null && !cohortId.isBlank()) {
            rows = assignmentRepository.findByTenantIdAndCohortIdOrderByCreatedAtDesc(tenantId, UUID.fromString(cohortId), page);
        } else {
            rows = assignmentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, page);
        }
        return Map.of("items", rows.stream().map(FundoAssignmentService::assignmentView).toList(), "limit", limit);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAssignment(UUID tenantId, String assignmentId) {
        AssignmentEntity a = assignmentRepository.findByTenantIdAndId(tenantId, UUID.fromString(assignmentId))
                .orElseThrow(() -> new IllegalArgumentException("assignment not found"));
        return assignmentView(a);
    }

    @Transactional
    public Map<String, Object> submit(UUID tenantId, String assignmentId, Map<String, Object> body) {
        AssignmentEntity a = assignmentRepository.findByTenantIdAndId(tenantId, UUID.fromString(assignmentId))
                .orElseThrow(() -> new IllegalArgumentException("assignment not found"));
        String subjectType = reqStr(body, "subjectType");
        String subjectId = reqStr(body, "subjectId");
        List<AssignmentSubmissionEntity> existing = submissionRepository
                .findByAssignmentIdAndSubjectTypeAndSubjectId(a.getId(), subjectType, subjectId);
        int nextNo = existing.stream().mapToInt(AssignmentSubmissionEntity::getSubmissionNo).max().orElse(0) + 1;

        AssignmentSubmissionEntity s = new AssignmentSubmissionEntity();
        s.setTenantId(tenantId);
        s.setAssignmentId(a.getId());
        s.setEnrolmentId(uuidOrNull(body.get("enrolmentId")));
        s.setSubjectType(subjectType);
        s.setSubjectId(subjectId);
        s.setSubmissionNo(nextNo);
        s.setSubmittedAt(OffsetDateTime.now());
        s.setBodyText(nullable(body, "bodyText"));
        s.setMediaRefJson(writeJson(body.get("mediaRefs")));
        s.setMarkingStatus("PENDING_MARK");
        submissionRepository.save(s);

        outbox.append("assignment", a.getId().toString(), FundoNativeEventTypes.ASSIGNMENT_SUBMITTED, Map.of(
                "tenantId", tenantId.toString(),
                "assignmentId", a.getId().toString(),
                "submissionId", s.getId().toString(),
                "subjectType", subjectType,
                "subjectId", subjectId,
                "submissionNo", nextNo));
        return submissionView(s);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listSubmissions(UUID tenantId, String assignmentId) {
        List<Map<String, Object>> items = submissionRepository
                .findByTenantIdAndAssignmentIdOrderBySubmissionNoAsc(tenantId, UUID.fromString(assignmentId))
                .stream().map(FundoAssignmentService::submissionView).toList();
        return Map.of("assignmentId", assignmentId, "items", items);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> markingQueue(UUID tenantId, String cohortId, int limit) {
        List<AssignmentSubmissionEntity> pending = submissionRepository
                .findByTenantIdAndMarkingStatusOrderByCreatedAtAsc(tenantId, "PENDING_MARK", PageRequest.of(0, limit));
        UUID cohort = (cohortId == null || cohortId.isBlank()) ? null : UUID.fromString(cohortId);
        List<Map<String, Object>> items = pending.stream()
                .filter(s -> {
                    if (cohort == null) return true;
                    return assignmentRepository.findByTenantIdAndId(tenantId, s.getAssignmentId())
                            .map(a -> cohort.equals(a.getCohortId())).orElse(false);
                })
                .map(FundoAssignmentService::submissionView)
                .toList();
        return Map.of("items", items, "limit", limit);
    }

    @Transactional
    public Map<String, Object> mark(UUID tenantId, String submissionId, String markerId, Map<String, Object> body) {
        AssignmentSubmissionEntity s = submissionRepository.findByTenantIdAndId(tenantId, UUID.fromString(submissionId))
                .orElseThrow(() -> new IllegalArgumentException("submission not found"));
        if (body.get("score") != null) {
            s.setScore(Integer.valueOf(body.get("score").toString()));
        }
        if (body.get("passed") != null) {
            s.setPassed(Boolean.valueOf(body.get("passed").toString()));
        }
        s.setFeedbackText(nullable(body, "feedbackText"));
        s.setRubricAppliedJson(writeJson(body.get("rubricApplied")));
        s.setMarkerId(markerId);
        s.setMarkedAt(OffsetDateTime.now());
        s.setMarkingStatus(str(body, "markingStatus", "MARKED"));
        s.setUpdatedAt(OffsetDateTime.now());
        submissionRepository.save(s);

        outbox.append("assignment", s.getAssignmentId().toString(), FundoNativeEventTypes.ASSIGNMENT_MARKED, Map.of(
                "tenantId", tenantId.toString(),
                "assignmentId", s.getAssignmentId().toString(),
                "submissionId", s.getId().toString(),
                "subjectType", s.getSubjectType(),
                "subjectId", s.getSubjectId(),
                "markingStatus", s.getMarkingStatus(),
                "passed", s.getPassed() == null ? false : s.getPassed()));
        return submissionView(s);
    }

    private static Map<String, Object> assignmentView(AssignmentEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("title", a.getTitle());
        m.put("instructions", a.getInstructions());
        m.put("courseId", a.getCourseId() == null ? null : a.getCourseId().toString());
        m.put("cohortId", a.getCohortId() == null ? null : a.getCohortId().toString());
        m.put("moduleId", a.getModuleId() == null ? null : a.getModuleId().toString());
        m.put("maxScore", a.getMaxScore());
        m.put("dueAt", a.getDueAt() == null ? null : a.getDueAt().toString());
        m.put("allowLate", a.isAllowLate());
        m.put("status", a.getStatus());
        return m;
    }

    private static Map<String, Object> submissionView(AssignmentSubmissionEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId().toString());
        m.put("assignmentId", s.getAssignmentId().toString());
        m.put("subjectType", s.getSubjectType());
        m.put("subjectId", s.getSubjectId());
        m.put("submissionNo", s.getSubmissionNo());
        m.put("submittedAt", s.getSubmittedAt() == null ? null : s.getSubmittedAt().toString());
        m.put("bodyText", s.getBodyText());
        m.put("markingStatus", s.getMarkingStatus());
        m.put("score", s.getScore());
        m.put("passed", s.getPassed());
        m.put("feedbackText", s.getFeedbackText());
        m.put("markerId", s.getMarkerId());
        m.put("markedAt", s.getMarkedAt() == null ? null : s.getMarkedAt().toString());
        return m;
    }

    private String writeJson(Object o) {
        if (o == null) return null;
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private static UUID uuidOrNull(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        return UUID.fromString(o.toString());
    }

    private static String reqStr(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key, String def) {
        Object v = body.get(key);
        return v == null || v.toString().isBlank() ? def : v.toString();
    }

    private static String nullable(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }
}
