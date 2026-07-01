package zw.gov.mohcc.impilo.learning.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.core.ai.LearningAiProvider;
import zw.gov.mohcc.impilo.learning.integration.LiveSessionIntegration;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningOutboxEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningOutboxRepository;

@Service
public class LearningNativeService {

    private final JdbcTemplate jdbcTemplate;
    private final LearningAiProvider aiProvider;
    private final LearningOutboxRepository outboxRepository;
    private final LiveSessionIntegration liveSessionIntegration;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LearningNativeService(
            JdbcTemplate jdbcTemplate,
            LearningAiProvider aiProvider,
            LearningOutboxRepository outboxRepository,
            LiveSessionIntegration liveSessionIntegration) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiProvider = aiProvider;
        this.outboxRepository = outboxRepository;
        this.liveSessionIntegration = liveSessionIntegration;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listCatalog(UUID tenantId, int limit) {
        List<Map<String, Object>> items = jdbcTemplate.query(
                """
                select id, code, title, description, category, level, status, language,
                       estimated_duration_minutes, mandatory, cpd_eligible
                from lrn_course
                where tenant_id = ? and status <> 'ARCHIVED'
                order by updated_at desc
                limit ?
                """,
                (rs, rowNum) -> mapCourseRow(rs),
                tenantId,
                limit);
        return Map.of("items", items, "limit", limit);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCourse(UUID tenantId, String courseId) {
        return jdbcTemplate.query(
                """
                select id, code, title, description, category, level, status, language,
                       estimated_duration_minutes, mandatory, cpd_eligible
                from lrn_course where tenant_id = ? and id = ?
                """,
                rs -> rs.next() ? mapCourseRow(rs) : null,
                tenantId,
                UUID.fromString(courseId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCourseStructure(UUID tenantId, String courseId) {
        Map<String, Object> course = getCourse(tenantId, courseId);
        if (course == null) return null;
        List<Map<String, Object>> modules = jdbcTemplate.query(
                """
                select id, title, description, sequence_no
                from lrn_course_module
                where tenant_id = ? and course_id = ?
                order by sequence_no asc
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    UUID moduleId = rs.getObject("id", UUID.class);
                    row.put("id", moduleId.toString());
                    row.put("title", rs.getString("title"));
                    row.put("description", rs.getString("description"));
                    row.put("sequenceNo", rs.getInt("sequence_no"));
                    row.put("lessons", listLessons(moduleId));
                    return row;
                },
                tenantId,
                UUID.fromString(courseId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.putAll(course);
        out.put("modules", modules);
        return Map.of("structure", out);
    }

    @Transactional
    public Map<String, Object> createCourse(UUID tenantId, String actorId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_course (
                  id, tenant_id, code, title, description, category, level, status, language,
                  estimated_duration_minutes, mandatory, cpd_eligible, cpd_points, version, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, now(), now())
                """,
                id,
                tenantId,
                stringVal(body, "code", "FUNDO-" + id.toString().substring(0, 8).toUpperCase()),
                stringVal(body, "title", "Untitled course"),
                stringVal(body, "description", ""),
                stringVal(body, "category", "GENERAL"),
                stringVal(body, "level", "FOUNDATION"),
                stringVal(body, "status", "DRAFT"),
                stringVal(body, "language", "en"),
                intVal(body.get("estimatedDurationMinutes"), 60),
                boolVal(body.get("mandatory"), false),
                boolVal(body.get("cpdEligible"), false),
                intVal(body.get("cpdPoints"), 0));
        return Map.of("course", getCourse(tenantId, id.toString()));
    }

    @Transactional
    public Map<String, Object> updateCourse(UUID tenantId, String courseId, Map<String, Object> body) {
        jdbcTemplate.update(
                """
                update lrn_course
                set title = coalesce(?, title),
                    description = coalesce(?, description),
                    category = coalesce(?, category),
                    level = coalesce(?, level),
                    status = coalesce(?, status),
                    language = coalesce(?, language),
                    mandatory = coalesce(?, mandatory),
                    cpd_eligible = coalesce(?, cpd_eligible),
                    updated_at = now()
                where tenant_id = ? and id = ?
                """,
                nullableString(body.get("title")),
                nullableString(body.get("description")),
                nullableString(body.get("category")),
                nullableString(body.get("level")),
                nullableString(body.get("status")),
                nullableString(body.get("language")),
                nullableBoolean(body.get("mandatory")),
                nullableBoolean(body.get("cpdEligible")),
                tenantId,
                UUID.fromString(courseId));
        return Map.of("course", getCourse(tenantId, courseId));
    }

    @Transactional
    public Map<String, Object> createModule(UUID tenantId, String courseId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_course_module (
                  id, tenant_id, course_id, title, description, sequence_no, status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                id,
                tenantId,
                UUID.fromString(courseId),
                stringVal(body, "title", "Module"),
                stringVal(body, "description", ""),
                intVal(body.get("sequenceNo"), nextSequence("lrn_course_module", "course_id", courseId)),
                stringVal(body, "status", "DRAFT"));
        return Map.of("module", Map.of("id", id.toString(), "courseId", courseId));
    }

    @Transactional
    public Map<String, Object> updateModule(UUID tenantId, String moduleId, Map<String, Object> body) {
        jdbcTemplate.update(
                """
                update lrn_course_module
                set title = coalesce(?, title),
                    description = coalesce(?, description),
                    status = coalesce(?, status),
                    updated_at = now()
                where tenant_id = ? and id = ?
                """,
                nullableString(body.get("title")),
                nullableString(body.get("description")),
                nullableString(body.get("status")),
                tenantId,
                UUID.fromString(moduleId));
        return Map.of("status", "updated", "moduleId", moduleId);
    }

    @Transactional
    public Map<String, Object> createLesson(UUID tenantId, String moduleId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_course_lesson (
                  id, tenant_id, module_id, title, content_type, content_body, content_ref, estimated_duration_minutes,
                  sequence_no, required, status, content_blocks_json, learner_preview_json, creator_notes_json, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                id,
                tenantId,
                UUID.fromString(moduleId),
                stringVal(body, "title", "Lesson"),
                stringVal(body, "contentType", "TEXT"),
                stringVal(body, "contentBody", ""),
                nullableString(body.get("contentRef")),
                intVal(body.get("estimatedDurationMinutes"), 15),
                intVal(body.get("sequenceNo"), nextSequence("lrn_course_lesson", "module_id", moduleId)),
                boolVal(body.get("required"), true),
                stringVal(body, "status", "DRAFT"),
                writeJson(body.getOrDefault("contentBlocks", List.of())),
                writeJson(body.getOrDefault("learnerPreview", Map.of())),
                writeJson(body.getOrDefault("creatorNotes", Map.of())));
        return Map.of("lesson", Map.of("id", id.toString(), "moduleId", moduleId));
    }

    @Transactional
    public Map<String, Object> updateLesson(UUID tenantId, String lessonId, Map<String, Object> body) {
        jdbcTemplate.update(
                """
                update lrn_course_lesson
                set title = coalesce(?, title),
                    content_body = coalesce(?, content_body),
                    content_ref = coalesce(?, content_ref),
                    status = coalesce(?, status),
                    content_blocks_json = coalesce(?, content_blocks_json),
                    learner_preview_json = coalesce(?, learner_preview_json),
                    creator_notes_json = coalesce(?, creator_notes_json),
                    updated_at = now()
                where tenant_id = ? and id = ?
                """,
                nullableString(body.get("title")),
                nullableString(body.get("contentBody")),
                nullableString(body.get("contentRef")),
                nullableString(body.get("status")),
                body.containsKey("contentBlocks") ? writeJson(body.get("contentBlocks")) : null,
                body.containsKey("learnerPreview") ? writeJson(body.get("learnerPreview")) : null,
                body.containsKey("creatorNotes") ? writeJson(body.get("creatorNotes")) : null,
                tenantId,
                UUID.fromString(lessonId));
        return Map.of("status", "updated", "lessonId", lessonId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myLearning(UUID tenantId, String subjectType, String subjectId) {
        List<Map<String, Object>> enrolments = jdbcTemplate.query(
                """
                select e.id, e.course_id, e.status, e.due_at, e.completed_at, c.title
                from lrn_enrolment e
                join lrn_course c on c.id = e.course_id
                where e.tenant_id = ? and e.subject_type = ? and e.subject_id = ?
                order by e.updated_at desc
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class).toString());
                    row.put("courseId", rs.getObject("course_id", UUID.class).toString());
                    row.put("courseTitle", rs.getString("title"));
                    row.put("status", rs.getString("status"));
                    row.put("dueAt", str(rs, "due_at"));
                    row.put("completedAt", str(rs, "completed_at"));
                    return row;
                },
                tenantId,
                subjectType,
                subjectId);
        List<Map<String, Object>> inProgress = enrolments.stream()
                .filter(e -> "IN_PROGRESS".equals(e.get("status")) || "ENROLLED".equals(e.get("status")))
                .toList();
        List<Map<String, Object>> completed = enrolments.stream()
                .filter(e -> "COMPLETED".equals(e.get("status")))
                .toList();
        List<Map<String, Object>> overdue = enrolments.stream()
                .filter(e -> e.get("dueAt") != null && !"COMPLETED".equals(e.get("status")))
                .toList();
        List<Map<String, Object>> certificates = jdbcTemplate.query(
                """
                select id, course_id, title, issued_at
                from lrn_certificate
                where tenant_id = ? and subject_type = ? and subject_id = ?
                order by issued_at desc
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getObject("id", UUID.class).toString(),
                        "courseId", rs.getObject("course_id", UUID.class).toString(),
                        "title", rs.getString("title"),
                        "issuedAt", str(rs, "issued_at")),
                tenantId,
                subjectType,
                subjectId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subjectType", subjectType);
        payload.put("subjectId", subjectId);
        payload.put("enrolled", enrolments);
        payload.put("inProgress", inProgress);
        payload.put("completed", completed);
        payload.put("overdue", overdue);
        payload.put("required", overdue);
        payload.put("certificates", certificates);
        payload.put("cpdEligibleCompletions", completed);
        payload.put("inProgressCount", inProgress.size());
        payload.put("completedCount", completed.size());
        payload.put("overdueCount", overdue.size());
        payload.put("requiredCount", overdue.size());
        payload.put("cpdEligibleCount", completed.size());
        return payload;
    }

    @Transactional
    public Map<String, Object> createEnrolment(UUID tenantId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_enrolment (
                  id, tenant_id, subject_type, subject_id, course_id, enrolment_type, status, assigned_by, assigned_at, due_at,
                  created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), ?, now(), now())
                """,
                id,
                tenantId,
                stringVal(body, "subjectType", "PROVIDER"),
                stringVal(body, "subjectId", "unknown"),
                UUID.fromString(stringVal(body, "courseId", "")),
                stringVal(body, "enrolmentType", "SELF"),
                "ENROLLED",
                stringVal(body, "sourceSystem", "fundo"),
                parseOffsetNullable(body.get("dueAt")));
        return Map.of("enrolment", Map.of("id", id.toString()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listEnrolments(UUID tenantId, String subjectType, String subjectId, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, course_id, status, due_at, completed_at
                from lrn_enrolment
                where tenant_id = ? and subject_type = ? and subject_id = ?
                order by updated_at desc
                limit ?
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getObject("id", UUID.class).toString(),
                        "courseId", rs.getObject("course_id", UUID.class).toString(),
                        "status", rs.getString("status"),
                        "dueAt", str(rs, "due_at"),
                        "completedAt", str(rs, "completed_at")),
                tenantId,
                subjectType,
                subjectId,
                limit);
        return Map.of("items", rows, "limit", limit);
    }

    @Transactional
    public Map<String, Object> startEnrolment(UUID tenantId, String enrolmentId) {
        jdbcTemplate.update(
                "update lrn_enrolment set status = 'IN_PROGRESS', updated_at = now() where tenant_id = ? and id = ?",
                tenantId,
                UUID.fromString(enrolmentId));
        return Map.of("status", "started", "enrolmentId", enrolmentId);
    }

    @Transactional
    public Map<String, Object> cancelEnrolment(UUID tenantId, String enrolmentId, Map<String, Object> body) {
        jdbcTemplate.update(
                "update lrn_enrolment set status = 'CANCELLED', updated_at = now() where tenant_id = ? and id = ?",
                tenantId,
                UUID.fromString(enrolmentId));
        return Map.of("status", "cancelled", "enrolmentId", enrolmentId, "reason", stringVal(body, "reason", "user"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> enrolmentProgress(UUID tenantId, String enrolmentId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, lesson_id, status, progress_percent, completed_at
                from lrn_course_progress
                where tenant_id = ? and enrolment_id = ?
                order by updated_at desc
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getObject("id", UUID.class).toString(),
                        "lessonId", rs.getObject("lesson_id", UUID.class) == null ? "" : rs.getObject("lesson_id", UUID.class).toString(),
                        "status", rs.getString("status"),
                        "progressPercent", rs.getInt("progress_percent"),
                        "completedAt", str(rs, "completed_at")),
                tenantId,
                UUID.fromString(enrolmentId));
        return Map.of("items", rows);
    }

    @Transactional
    public Map<String, Object> recordProgress(UUID tenantId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        String enrolmentId = stringVal(body, "enrolmentId", "");
        String lessonId = stringVal(body, "lessonId", "");
        int percent = intVal(body.get("progressPercent"), 0);
        UUID courseId = jdbcTemplate.query(
                "select course_id from lrn_enrolment where tenant_id = ? and id = ?",
                rs -> rs.next() ? rs.getObject("course_id", UUID.class) : null,
                tenantId,
                UUID.fromString(enrolmentId));
        jdbcTemplate.update(
                """
                insert into lrn_course_progress (
                  id, tenant_id, enrolment_id, course_id, lesson_id, subject_type, subject_id, status,
                  progress_percent, started_at, completed_at, last_accessed_at, created_at, updated_at
                )
                select ?, ?, e.id, e.course_id, ?, e.subject_type, e.subject_id, ?, ?, now(),
                       case when ? >= 100 then now() else null end, now(), now(), now()
                from lrn_enrolment e
                where e.tenant_id = ? and e.id = ?
                """,
                id,
                tenantId,
                lessonId.isBlank() ? null : UUID.fromString(lessonId),
                percent >= 100 ? "COMPLETED" : "STARTED",
                percent,
                percent,
                tenantId,
                UUID.fromString(enrolmentId));
        if (percent >= 100) {
            jdbcTemplate.update(
                    "update lrn_enrolment set status = 'COMPLETED', completed_at = now(), updated_at = now() where tenant_id = ? and id = ?",
                    tenantId,
                    UUID.fromString(enrolmentId));
        }
        return Map.of("status", "recorded", "progressId", id.toString(), "courseId", courseId == null ? "" : courseId.toString());
    }

    @Transactional
    public Map<String, Object> openLesson(UUID tenantId, String lessonId, Map<String, Object> body) {
        return Map.of("status", "opened", "lessonId", lessonId, "enrolmentId", stringVal(body, "enrolmentId", ""));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listCourseAssessments(UUID tenantId, String courseId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, title, assessment_type, pass_mark, max_attempts, status
                from lrn_assessment where tenant_id = ? and course_id = ? and status <> 'ARCHIVED'
                order by updated_at desc
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getObject("id", UUID.class).toString(),
                        "title", rs.getString("title"),
                        "assessmentType", rs.getString("assessment_type"),
                        "passMark", rs.getInt("pass_mark"),
                        "maxAttempts", rs.getInt("max_attempts"),
                        "status", rs.getString("status")),
                tenantId,
                UUID.fromString(courseId));
        return Map.of("items", rows);
    }

    @Transactional
    public Map<String, Object> createAssessment(UUID tenantId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_assessment (
                  id, tenant_id, course_id, module_id, title, description, assessment_type, pass_mark, max_attempts, status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                id,
                tenantId,
                UUID.fromString(stringVal(body, "courseId", "")),
                nullableUuid(body.get("moduleId")),
                stringVal(body, "title", "Assessment"),
                stringVal(body, "description", ""),
                stringVal(body, "assessmentType", "QUIZ"),
                intVal(body.get("passMark"), 70),
                intVal(body.get("maxAttempts"), 3),
                stringVal(body, "status", "DRAFT"));
        return Map.of("assessment", Map.of("id", id.toString()));
    }

    @Transactional
    public Map<String, Object> updateAssessment(UUID tenantId, String assessmentId, Map<String, Object> body) {
        jdbcTemplate.update(
                """
                update lrn_assessment
                set title = coalesce(?, title),
                    description = coalesce(?, description),
                    assessment_type = coalesce(?, assessment_type),
                    pass_mark = coalesce(?, pass_mark),
                    max_attempts = coalesce(?, max_attempts),
                    status = coalesce(?, status),
                    updated_at = now()
                where tenant_id = ? and id = ?
                """,
                nullableString(body.get("title")),
                nullableString(body.get("description")),
                nullableString(body.get("assessmentType")),
                nullableInteger(body.get("passMark")),
                nullableInteger(body.get("maxAttempts")),
                nullableString(body.get("status")),
                tenantId,
                UUID.fromString(assessmentId));
        return Map.of("assessmentId", assessmentId, "status", "updated");
    }

    @Transactional
    public Map<String, Object> addQuestion(UUID tenantId, String assessmentId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_assessment_question (
                  id, assessment_id, question_type, prompt, options_json, correct_answer_json, sequence_no, points
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                UUID.fromString(assessmentId),
                stringVal(body, "questionType", "MULTIPLE_CHOICE"),
                stringVal(body, "prompt", "Question"),
                writeJson(body.getOrDefault("options", List.of())),
                writeJson(body.getOrDefault("correctAnswer", "")),
                intVal(body.get("sequenceNo"), nextQuestionSequence(assessmentId)),
                intVal(body.get("points"), 1));
        return Map.of("question", Map.of("id", id.toString(), "assessmentId", assessmentId));
    }

    @Transactional
    public Map<String, Object> updateQuestion(String assessmentId, String questionId, Map<String, Object> body) {
        jdbcTemplate.update(
                """
                update lrn_assessment_question
                set question_type = coalesce(?, question_type),
                    prompt = coalesce(?, prompt),
                    options_json = coalesce(?, options_json),
                    correct_answer_json = coalesce(?, correct_answer_json),
                    points = coalesce(?, points)
                where assessment_id = ? and id = ?
                """,
                nullableString(body.get("questionType")),
                nullableString(body.get("prompt")),
                body.containsKey("options") ? writeJson(body.get("options")) : null,
                body.containsKey("correctAnswer") ? writeJson(body.get("correctAnswer")) : null,
                nullableInteger(body.get("points")),
                UUID.fromString(assessmentId),
                UUID.fromString(questionId));
        return Map.of("questionId", questionId, "status", "updated");
    }

    @Transactional
    public Map<String, Object> submitAttempt(UUID tenantId, String assessmentId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        String subjectType = stringVal(body, "subjectType", "PROVIDER");
        String subjectId = stringVal(body, "subjectId", "unknown");
        int attemptNo = nextAttemptNo(assessmentId, subjectType, subjectId);
        Integer score = nullableInteger(body.get("score"));
        Boolean passed = score == null ? null : score >= 70;
        jdbcTemplate.update(
                """
                insert into lrn_assessment_attempt (
                  id, tenant_id, assessment_id, enrolment_id, subject_type, subject_id, attempt_no, score, passed, submitted_at, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                id,
                tenantId,
                UUID.fromString(assessmentId),
                nullableUuid(body.get("enrolmentId")),
                subjectType,
                subjectId,
                attemptNo,
                score,
                passed);
        return Map.of("attempt", Map.of("id", id.toString(), "attemptNo", attemptNo, "score", score, "passed", passed));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listAttempts(UUID tenantId, String assessmentId, String subjectType, String subjectId) {
        List<Map<String, Object>> items = jdbcTemplate.query(
                """
                select id, attempt_no, score, passed, submitted_at
                from lrn_assessment_attempt
                where tenant_id = ? and assessment_id = ? and subject_type = ? and subject_id = ?
                order by attempt_no desc
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getObject("id", UUID.class).toString(),
                        "attemptNo", rs.getInt("attempt_no"),
                        "score", rs.getObject("score"),
                        "passed", rs.getObject("passed"),
                        "submittedAt", str(rs, "submitted_at")),
                tenantId,
                UUID.fromString(assessmentId),
                subjectType,
                subjectId);
        return Map.of("items", items);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pendingReviews(UUID tenantId, String assessmentId, Integer limit) {
        int cap = limit == null ? 20 : Math.max(1, limit);
        List<Map<String, Object>> items = jdbcTemplate.query(
                """
                select id, subject_type, subject_id, attempt_no, score, submitted_at
                from lrn_assessment_attempt
                where tenant_id = ? and assessment_id = ? and score is null
                order by submitted_at desc
                limit ?
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getObject("id", UUID.class).toString(),
                        "subjectType", rs.getString("subject_type"),
                        "subjectId", rs.getString("subject_id"),
                        "attemptNo", rs.getInt("attempt_no"),
                        "score", rs.getObject("score"),
                        "submittedAt", str(rs, "submitted_at")),
                tenantId,
                UUID.fromString(assessmentId),
                cap);
        return Map.of("items", items, "limit", cap);
    }

    @Transactional
    public Map<String, Object> manualReview(UUID tenantId, String attemptId, Map<String, Object> body) {
        Integer score = nullableInteger(body.get("score"));
        jdbcTemplate.update(
                "update lrn_assessment_attempt set score = ?, passed = ?, submitted_at = coalesce(submitted_at, now()) where tenant_id = ? and id = ?",
                score,
                score == null ? null : score >= 70,
                tenantId,
                UUID.fromString(attemptId));
        return Map.of("status", "reviewed", "attemptId", attemptId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listCertificates(UUID tenantId, String subjectType, String subjectId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, enrolment_id, course_id, certificate_number, title, issued_at, valid_until, status, cpd_eligible, cpd_points
                from lrn_certificate
                where tenant_id = ? and subject_type = ? and subject_id = ?
                order by issued_at desc
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class).toString());
                    row.put("enrolmentId", rs.getObject("enrolment_id", UUID.class).toString());
                    row.put("courseId", rs.getObject("course_id", UUID.class).toString());
                    row.put("certificateNumber", rs.getString("certificate_number"));
                    row.put("title", rs.getString("title"));
                    row.put("issuedAt", str(rs, "issued_at"));
                    row.put("validUntil", str(rs, "valid_until"));
                    row.put("status", rs.getString("status"));
                    row.put("cpdEligible", rs.getBoolean("cpd_eligible"));
                    row.put("cpdPoints", rs.getInt("cpd_points"));
                    return row;
                },
                tenantId,
                subjectType,
                subjectId);
        return Map.of("items", rows);
    }

    @Transactional
    public Map<String, Object> issueCertificate(UUID tenantId, String enrolmentId) {
        UUID id = UUID.randomUUID();
        String certificateNumber = "FND-" + OffsetDateTime.now().toLocalDate() + "-" + id.toString().substring(0, 8).toUpperCase();
        jdbcTemplate.update(
                """
                insert into lrn_certificate (
                  id, tenant_id, enrolment_id, course_id, subject_type, subject_id,
                  certificate_number, title, issued_at, status, cpd_eligible, cpd_points
                )
                select ?, ?, e.id, e.course_id, e.subject_type, e.subject_id,
                       ?, c.title, now(), 'ISSUED', c.cpd_eligible, c.cpd_points
                from lrn_enrolment e
                join lrn_course c on c.id = e.course_id
                where e.tenant_id = ? and e.id = ?
                """,
                id,
                tenantId,
                certificateNumber,
                tenantId,
                UUID.fromString(enrolmentId));
        return Map.of("certificate", Map.of("id", id.toString(), "certificateNumber", certificateNumber));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cpdEvidence(UUID tenantId, String subjectType, String subjectId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, course_id, title, issued_at, cpd_points, cpd_eligible
                from lrn_certificate
                where tenant_id = ? and subject_type = ? and subject_id = ? and cpd_eligible = true
                order by issued_at desc
                """,
                (rs, rowNum) -> Map.of(
                        "certificateId", rs.getObject("id", UUID.class).toString(),
                        "courseId", rs.getObject("course_id", UUID.class).toString(),
                        "courseTitle", rs.getString("title"),
                        "completedAt", str(rs, "issued_at"),
                        "cpdPoints", rs.getInt("cpd_points"),
                        "verifiedState", "PENDING_COUNCIL_REVIEW"),
                tenantId,
                subjectType,
                subjectId);
        return Map.of("subjectType", subjectType, "subjectId", subjectId, "items", rows);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reportOverview(UUID tenantId) {
        int courses = jdbcTemplate.queryForObject("select count(*) from lrn_course where tenant_id = ?", Integer.class, tenantId);
        int enrolments = jdbcTemplate.queryForObject("select count(*) from lrn_enrolment where tenant_id = ?", Integer.class, tenantId);
        int completed = jdbcTemplate.queryForObject(
                "select count(*) from lrn_enrolment where tenant_id = ? and status = 'COMPLETED'",
                Integer.class,
                tenantId);
        int certificates = jdbcTemplate.queryForObject("select count(*) from lrn_certificate where tenant_id = ?", Integer.class, tenantId);
        return Map.of(
                "courses", courses,
                "enrolments", enrolments,
                "completed", completed,
                "certificates", certificates);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reportCourseCompletions(UUID tenantId, String courseId, Integer limit) {
        int cap = limit == null ? 100 : Math.max(1, limit);
        String sql = """
                select e.id, e.course_id, c.title, e.subject_type, e.subject_id, e.status, e.completed_at
                from lrn_enrolment e
                join lrn_course c on c.id = e.course_id
                where e.tenant_id = ?
                """;
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (courseId != null && !courseId.isBlank()) {
            sql += " and e.course_id = ? ";
            args.add(UUID.fromString(courseId));
        }
        sql += " order by e.updated_at desc limit ? ";
        args.add(cap);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> Map.of(
                        "enrolmentId", rs.getObject("id", UUID.class).toString(),
                        "courseId", rs.getObject("course_id", UUID.class).toString(),
                        "courseTitle", rs.getString("title"),
                        "subjectType", rs.getString("subject_type"),
                        "subjectId", rs.getString("subject_id"),
                        "status", rs.getString("status"),
                        "completedAt", str(rs, "completed_at")),
                args.toArray());
        return Map.of("items", rows, "limit", cap);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reportOverdue(UUID tenantId, Integer limit) {
        int cap = limit == null ? 100 : Math.max(1, limit);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, course_id, subject_type, subject_id, due_at, status
                from lrn_enrolment
                where tenant_id = ? and due_at is not null and status <> 'COMPLETED'
                order by due_at asc
                limit ?
                """,
                (rs, rowNum) -> Map.of(
                        "enrolmentId", rs.getObject("id", UUID.class).toString(),
                        "courseId", rs.getObject("course_id", UUID.class).toString(),
                        "subjectType", rs.getString("subject_type"),
                        "subjectId", rs.getString("subject_id"),
                        "dueAt", str(rs, "due_at"),
                        "status", rs.getString("status")),
                tenantId,
                cap);
        return Map.of("items", rows, "limit", cap);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reportAssessmentPerformance(UUID tenantId, Integer limit) {
        int cap = limit == null ? 100 : Math.max(1, limit);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, assessment_id, subject_type, subject_id, score, passed, submitted_at
                from lrn_assessment_attempt
                where tenant_id = ?
                order by submitted_at desc
                limit ?
                """,
                (rs, rowNum) -> Map.of(
                        "attemptId", rs.getObject("id", UUID.class).toString(),
                        "assessmentId", rs.getObject("assessment_id", UUID.class).toString(),
                        "subjectType", rs.getString("subject_type"),
                        "subjectId", rs.getString("subject_id"),
                        "score", rs.getObject("score"),
                        "passed", rs.getObject("passed"),
                        "submittedAt", str(rs, "submitted_at")),
                tenantId,
                cap);
        return Map.of("items", rows, "limit", cap);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reportCohortCompletions(UUID tenantId, String cohortId) {
        String sql = """
                select c.id as cohort_id, c.title as cohort_title, m.subject_type, m.subject_id, e.status, e.completed_at
                from lrn_learning_cohort c
                join lrn_cohort_membership m on m.cohort_id = c.id
                left join lrn_enrolment e on e.tenant_id = c.tenant_id
                    and e.subject_type = m.subject_type and e.subject_id = m.subject_id and e.course_id = c.course_id
                where c.tenant_id = ?
                """;
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (cohortId != null && !cohortId.isBlank()) {
            sql += " and c.id = ? ";
            args.add(UUID.fromString(cohortId));
        }
        List<Map<String, Object>> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> Map.of(
                        "cohortId", rs.getObject("cohort_id", UUID.class).toString(),
                        "cohortTitle", rs.getString("cohort_title"),
                        "subjectType", rs.getString("subject_type"),
                        "subjectId", rs.getString("subject_id"),
                        "status", rs.getString("status"),
                        "completedAt", str(rs, "completed_at")),
                args.toArray());
        return Map.of("items", rows);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> learningRecord(UUID tenantId, String subjectType, String subjectId) {
        Map<String, Object> my = myLearning(tenantId, subjectType, subjectId);
        return Map.of(
                "subjectType", subjectType,
                "subjectId", subjectId,
                "enrolments", my.get("enrolled"),
                "completed", my.get("completed"),
                "certificates", my.get("certificates"),
                "cpdEvidence", cpdEvidence(tenantId, subjectType, subjectId).get("items"));
    }

    @Transactional
    public Map<String, Object> aiGenerate(UUID tenantId, String actorId, Map<String, Object> body) {
        String generationType = stringVal(body, "generationType", "course-outline");
        Map<String, Object> generated = switch (generationType) {
            case "lesson-content" -> aiProvider.generateLessonContent(body);
            case "quiz" -> aiProvider.generateQuiz(body);
            case "survey" -> aiProvider.generateSurvey(body);
            case "summary" -> aiProvider.generateSummary(body);
            case "facilitator-guide" -> aiProvider.generateFacilitatorGuide(body);
            case "voiceover-script" -> aiProvider.generateVoiceoverScript(body);
            default -> aiProvider.generateCourseOutline(body);
        };
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_ai_generation_draft (
                  id, tenant_id, generation_type, target_course_id, created_by, created_by_ai, model_provider,
                  prompt, source_documents_json, request_json, output_json, review_status, human_reviewer, created_at, updated_at
                ) values (?, ?, ?, ?, ?, true, ?, ?, ?, ?, ?, 'PENDING_REVIEW', null, now(), now())
                """,
                id,
                tenantId,
                generationType,
                nullableUuid(body.get("targetCourseId")),
                actorId,
                aiProvider.providerName(),
                stringVal(body, "prompt", ""),
                writeJson(body.getOrDefault("sourceDocuments", List.of())),
                writeJson(body),
                writeJson(generated));
        return Map.of(
                "draftId", id.toString(),
                "createdByAi", true,
                "modelProvider", aiProvider.providerName(),
                "reviewStatus", "PENDING_REVIEW",
                "output", generated);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listLibraryResources(UUID tenantId, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, title, description, resource_type, file_name, mime_type, storage_ref,
                       uploaded_by, owner, language, tags_json, review_status, source_attribution, archived_at, created_at, updated_at
                from lrn_library_resource
                where tenant_id = ?
                order by updated_at desc
                limit ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class).toString());
                    row.put("title", rs.getString("title"));
                    row.put("description", rs.getString("description"));
                    row.put("resourceType", rs.getString("resource_type"));
                    row.put("fileName", rs.getString("file_name"));
                    row.put("mimeType", rs.getString("mime_type"));
                    row.put("storageRef", rs.getString("storage_ref"));
                    row.put("uploadedBy", rs.getString("uploaded_by"));
                    row.put("owner", rs.getString("owner"));
                    row.put("language", rs.getString("language"));
                    row.put("tags", readJson(rs.getString("tags_json")));
                    row.put("reviewStatus", rs.getString("review_status"));
                    row.put("sourceAttribution", rs.getString("source_attribution"));
                    row.put("archivedAt", str(rs, "archived_at"));
                    row.put("createdAt", str(rs, "created_at"));
                    row.put("updatedAt", str(rs, "updated_at"));
                    return row;
                },
                tenantId,
                limit);
        return Map.of("items", rows, "limit", limit);
    }

    @Transactional
    public Map<String, Object> createLibraryResource(UUID tenantId, String actorId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_library_resource (
                  id, tenant_id, title, description, resource_type, file_name, mime_type, storage_ref,
                  uploaded_by, owner, language, tags_json, metadata_json, review_status, source_attribution, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                id,
                tenantId,
                stringVal(body, "title", "Untitled resource"),
                stringVal(body, "description", ""),
                stringVal(body, "resourceType", "DOCUMENT"),
                nullableString(body.get("fileName")),
                nullableString(body.get("mimeType")),
                nullableString(body.get("storageRef")),
                actorId,
                stringVal(body, "owner", actorId),
                nullableString(body.get("language")),
                writeJson(body.getOrDefault("tags", List.of())),
                writeJson(body.getOrDefault("metadata", Map.of())),
                stringVal(body, "reviewStatus", "DRAFT"),
                nullableString(body.get("sourceAttribution")));
        return Map.of("resource", Map.of("id", id.toString()));
    }

    @Transactional
    public Map<String, Object> linkLibraryResource(UUID tenantId, String resourceId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_library_usage_link (
                  id, tenant_id, resource_id, link_type, linked_entity_id, metadata_json, created_at
                ) values (?, ?, ?, ?, ?, ?, now())
                """,
                id,
                tenantId,
                UUID.fromString(resourceId),
                stringVal(body, "linkType", "LESSON"),
                stringVal(body, "linkedEntityId", ""),
                writeJson(body.getOrDefault("metadata", Map.of())));
        return Map.of("usageLink", Map.of("id", id.toString(), "resourceId", resourceId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listMedia(UUID tenantId, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, title, media_type, status, file_name, mime_type, storage_ref, transcript, caption_ref,
                       voiceover_script, recording_type, source_lesson_id, duration_seconds, metadata_json, created_by, created_at, updated_at
                from lrn_media_asset
                where tenant_id = ?
                order by updated_at desc
                limit ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class).toString());
                    row.put("title", rs.getString("title"));
                    row.put("mediaType", rs.getString("media_type"));
                    row.put("status", rs.getString("status"));
                    row.put("fileName", rs.getString("file_name"));
                    row.put("mimeType", rs.getString("mime_type"));
                    row.put("storageRef", rs.getString("storage_ref"));
                    row.put("transcript", rs.getString("transcript"));
                    row.put("captionRef", rs.getString("caption_ref"));
                    row.put("voiceoverScript", rs.getString("voiceover_script"));
                    row.put("recordingType", rs.getString("recording_type"));
                    row.put("sourceLessonId", rs.getObject("source_lesson_id") == null ? null : rs.getObject("source_lesson_id", UUID.class).toString());
                    row.put("durationSeconds", rs.getObject("duration_seconds"));
                    row.put("metadata", readJson(rs.getString("metadata_json")));
                    row.put("createdBy", rs.getString("created_by"));
                    row.put("createdAt", str(rs, "created_at"));
                    row.put("updatedAt", str(rs, "updated_at"));
                    return row;
                },
                tenantId,
                limit);
        return Map.of("items", rows, "limit", limit);
    }

    @Transactional
    public Map<String, Object> createMedia(UUID tenantId, String actorId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_media_asset (
                  id, tenant_id, title, media_type, status, file_name, mime_type, storage_ref, transcript, caption_ref,
                  voiceover_script, recording_type, source_lesson_id, duration_seconds, metadata_json, created_by, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                id,
                tenantId,
                stringVal(body, "title", "Media draft"),
                stringVal(body, "mediaType", "VIDEO"),
                stringVal(body, "status", "DRAFT"),
                nullableString(body.get("fileName")),
                nullableString(body.get("mimeType")),
                nullableString(body.get("storageRef")),
                nullableString(body.get("transcript")),
                nullableString(body.get("captionRef")),
                nullableString(body.get("voiceoverScript")),
                nullableString(body.get("recordingType")),
                nullableUuid(body.get("sourceLessonId")),
                nullableInteger(body.get("durationSeconds")),
                writeJson(body.getOrDefault("metadata", Map.of())),
                actorId);
        return Map.of("media", Map.of("id", id.toString()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listNotifications(UUID tenantId, String subjectType, String subjectId, int limit) {
        List<Map<String, Object>> items = jdbcTemplate.query(
                """
                select id, event_code, title, message, channel_preference, scheduled_at, due_at, reminder_at, recurrence, status, metadata_json, created_at, sent_at
                from lrn_learning_notification
                where tenant_id = ? and subject_type = ? and subject_id = ?
                order by created_at desc
                limit ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class).toString());
                    row.put("eventCode", rs.getString("event_code"));
                    row.put("title", rs.getString("title"));
                    row.put("message", rs.getString("message"));
                    row.put("channelPreference", rs.getString("channel_preference"));
                    row.put("scheduledAt", str(rs, "scheduled_at"));
                    row.put("dueAt", str(rs, "due_at"));
                    row.put("reminderAt", str(rs, "reminder_at"));
                    row.put("recurrence", rs.getString("recurrence"));
                    row.put("status", rs.getString("status"));
                    row.put("metadata", readJson(rs.getString("metadata_json")));
                    row.put("createdAt", str(rs, "created_at"));
                    row.put("sentAt", str(rs, "sent_at"));
                    return row;
                },
                tenantId,
                subjectType,
                subjectId,
                limit);
        return Map.of("items", items, "limit", limit);
    }

    @Transactional
    public Map<String, Object> scheduleNotification(UUID tenantId, String actorId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_learning_notification (
                  id, tenant_id, subject_type, subject_id, event_code, title, message, channel_preference,
                  scheduled_at, due_at, reminder_at, recurrence, status, metadata_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
                id,
                tenantId,
                stringVal(body, "subjectType", "PROVIDER"),
                stringVal(body, "subjectId", actorId),
                stringVal(body, "eventCode", "learning.notification.custom"),
                stringVal(body, "title", "Learning notification"),
                stringVal(body, "message", ""),
                nullableString(body.get("channelPreference")),
                parseOffsetNullable(body.get("scheduledAt")),
                parseOffsetNullable(body.get("dueAt")),
                parseOffsetNullable(body.get("reminderAt")),
                nullableString(body.get("recurrence")),
                stringVal(body, "status", "PENDING"),
                writeJson(body.getOrDefault("metadata", Map.of())));
        return Map.of("notification", Map.of("id", id.toString()));
    }

    @Transactional
    public Map<String, Object> createInteractiveActivity(UUID tenantId, String actorId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_interactive_activity (
                  id, tenant_id, course_id, lesson_id, activity_type, title, config_json, status, created_by, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                id,
                tenantId,
                nullableUuid(body.get("courseId")),
                nullableUuid(body.get("lessonId")),
                stringVal(body, "activityType", "POLL"),
                stringVal(body, "title", "Interactive activity"),
                writeJson(body.getOrDefault("config", Map.of())),
                stringVal(body, "status", "PUBLISHED"),
                actorId);
        return Map.of("activity", Map.of("id", id.toString()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listInteractiveActivities(UUID tenantId, String courseId, String lessonId, int limit) {
        String sql = """
                select id, activity_type, title, config_json, status, created_by, created_at
                from lrn_interactive_activity
                where tenant_id = ?
                """;
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (courseId != null && !courseId.isBlank()) {
            sql += " and course_id = ? ";
            args.add(UUID.fromString(courseId));
        }
        if (lessonId != null && !lessonId.isBlank()) {
            sql += " and lesson_id = ? ";
            args.add(UUID.fromString(lessonId));
        }
        sql += " order by created_at desc limit ? ";
        args.add(limit);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> Map.of(
                        "id", rs.getObject("id", UUID.class).toString(),
                        "activityType", rs.getString("activity_type"),
                        "title", rs.getString("title"),
                        "config", readJson(rs.getString("config_json")),
                        "status", rs.getString("status"),
                        "createdBy", rs.getString("created_by"),
                        "createdAt", str(rs, "created_at")),
                args.toArray());
        return Map.of("items", rows, "limit", limit);
    }

    @Transactional
    public Map<String, Object> submitInteractiveResponse(UUID tenantId, String activityId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        String subjectType = stringVal(body, "subjectType", "USER_HEALTH_ID");
        String subjectId = stringVal(body, "subjectId", "unknown");
        String activityType = jdbcTemplate.query(
                "select activity_type from lrn_interactive_activity where tenant_id = ? and id = ?",
                rs -> rs.next() ? rs.getString("activity_type") : "POLL",
                tenantId,
                UUID.fromString(activityId));
        jdbcTemplate.update(
                """
                insert into lrn_interactive_response (
                  id, activity_id, tenant_id, subject_type, subject_id, response_json, created_at
                ) values (?, ?, ?, ?, ?, ?, now())
                """,
                id,
                UUID.fromString(activityId),
                tenantId,
                subjectType,
                subjectId,
                writeJson(body.getOrDefault("response", Map.of())));
        appendOutboxEvent(
                "InteractiveActivity",
                activityId,
                "impilo.learning.interactive.response.submitted.v1",
                Map.of(
                        "tenantId", tenantId.toString(),
                        "activityId", activityId,
                        "subjectType", subjectType,
                        "subjectId", subjectId));
        if ("SURVEY".equalsIgnoreCase(activityType) || "FEEDBACK".equalsIgnoreCase(activityType) || "REFLECTION".equalsIgnoreCase(activityType)) {
            appendOutboxEvent(
                    "InteractiveActivity",
                    activityId,
                    "impilo.learning.survey.response.submitted.v1",
                    Map.of(
                            "tenantId", tenantId.toString(),
                            "activityId", activityId,
                            "activityType", activityType,
                            "subjectType", subjectType,
                            "subjectId", subjectId));
        }
        return Map.of("response", Map.of("id", id.toString()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listInteractiveResponses(UUID tenantId, String activityId, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, subject_type, subject_id, response_json, created_at
                from lrn_interactive_response
                where tenant_id = ? and activity_id = ?
                order by created_at desc
                limit ?
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getObject("id", UUID.class).toString(),
                        "subjectType", rs.getString("subject_type"),
                        "subjectId", rs.getString("subject_id"),
                        "response", readJson(rs.getString("response_json")),
                        "createdAt", str(rs, "created_at")),
                tenantId,
                UUID.fromString(activityId),
                limit);
        return Map.of("items", rows, "limit", limit);
    }

    @Transactional
    public Map<String, Object> createCohort(UUID tenantId, String actorId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_learning_cohort (
                  id, tenant_id, course_id, code, title, status, starts_at, ends_at, created_by, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                id,
                tenantId,
                UUID.fromString(stringVal(body, "courseId", "")),
                stringVal(body, "code", "COHORT-" + id.toString().substring(0, 8).toUpperCase()),
                stringVal(body, "title", "Learning cohort"),
                stringVal(body, "status", "ACTIVE"),
                parseOffsetNullable(body.get("startsAt")),
                parseOffsetNullable(body.get("endsAt")),
                actorId);
        return Map.of("cohort", Map.of("id", id.toString()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listCohorts(UUID tenantId, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, course_id, code, title, status, starts_at, ends_at, created_by, created_at
                from lrn_learning_cohort
                where tenant_id = ?
                order by created_at desc
                limit ?
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getObject("id", UUID.class).toString(),
                        "courseId", rs.getObject("course_id", UUID.class).toString(),
                        "code", rs.getString("code"),
                        "title", rs.getString("title"),
                        "status", rs.getString("status"),
                        "startsAt", str(rs, "starts_at"),
                        "endsAt", str(rs, "ends_at"),
                        "createdBy", rs.getString("created_by"),
                        "createdAt", str(rs, "created_at")),
                tenantId,
                limit);
        return Map.of("items", rows, "limit", limit);
    }

    @Transactional
    public Map<String, Object> addCohortMember(UUID tenantId, String cohortId, String actorId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into lrn_cohort_membership (
                  id, cohort_id, tenant_id, subject_type, subject_id, assigned_at, assigned_by
                ) values (?, ?, ?, ?, ?, now(), ?)
                on conflict (cohort_id, subject_type, subject_id) do nothing
                """,
                id,
                UUID.fromString(cohortId),
                tenantId,
                stringVal(body, "subjectType", "PROVIDER"),
                stringVal(body, "subjectId", ""),
                actorId);
        return Map.of("membership", Map.of("id", id.toString(), "cohortId", cohortId));
    }

    @Transactional
    public Map<String, Object> createScheduledSession(UUID tenantId, String actorId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        UUID courseId = UUID.fromString(stringVal(body, "courseId", ""));
        String sessionType = stringVal(body, "sessionType", "VIRTUAL");
        Map<String, Object> metadata = new LinkedHashMap<>();
        Object rawMetadata = body.get("metadata");
        if (rawMetadata instanceof Map<?, ?> rawMap) {
            rawMap.forEach((k, v) -> metadata.put(String.valueOf(k), v));
        }

        String liveEventId = null;
        String impiloLiveJoinPath = null;
        if (shouldScheduleImpiloLive(sessionType, body)) {
            List<String> trainerIds = new ArrayList<>();
            String facilitator = nullableString(body.get("facilitator"));
            if (facilitator != null && !facilitator.isBlank()) {
                trainerIds.add(facilitator);
            }
            Object trainers = body.get("trainerIds");
            if (trainers instanceof List<?> list) {
                for (Object t : list) {
                    if (t != null) {
                        trainerIds.add(t.toString());
                    }
                }
            }
            Map<String, Object> livePayload = liveSessionIntegration.buildFundoWebinarPayload(
                    courseId,
                    stringVal(body, "title", "Scheduled session"),
                    nullableString(body.get("description")),
                    body.get("startsAt"),
                    body.get("endsAt"),
                    trainerIds,
                    boolVal(body.get("cpdEnabled"), false),
                    body.get("cpdPoints"),
                    intVal(body.get("attendanceThresholdMinutes"), 30),
                    boolVal(body.get("replayAllowed"), true));
            var liveEvent = liveSessionIntegration.scheduleFundoWebinar(livePayload);
            if (liveEvent.isPresent()) {
                liveEventId = String.valueOf(liveEvent.get().get("id"));
                impiloLiveJoinPath = "/live/event/" + liveEventId;
                metadata.put("impiloLiveEventId", liveEventId);
                metadata.put("impiloLiveJoinPath", impiloLiveJoinPath);
                metadata.put("impiloLiveMode", "WEBINAR_CPD");
            }
        }

        jdbcTemplate.update(
                """
                insert into lrn_scheduled_learning_session (
                  id, tenant_id, course_id, cohort_id, title, session_type, starts_at, ends_at,
                  facilitator, location_ref, metadata_json, created_by, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
                id,
                tenantId,
                courseId,
                nullableUuid(body.get("cohortId")),
                stringVal(body, "title", "Scheduled session"),
                sessionType,
                parseOffsetRequired(body.get("startsAt")),
                parseOffsetNullable(body.get("endsAt")),
                nullableString(body.get("facilitator")),
                nullableString(body.get("locationRef")),
                writeJson(metadata.isEmpty() ? body.getOrDefault("metadata", Map.of()) : metadata),
                actorId);

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("id", id.toString());
        if (liveEventId != null) {
            session.put("impiloLiveEventId", liveEventId);
            session.put("impiloLiveJoinPath", impiloLiveJoinPath);
        }
        return Map.of("session", session);
    }

    private boolean shouldScheduleImpiloLive(String sessionType, Map<String, Object> body) {
        if (!liveSessionIntegration.isEnabled()) {
            return false;
        }
        if (Boolean.FALSE.equals(body.get("useImpiloLive")) || Boolean.FALSE.equals(body.get("use_impilo_live"))) {
            return false;
        }
        String normalized = sessionType == null ? "" : sessionType.trim().toUpperCase();
        return normalized.contains("VIRTUAL") || normalized.contains("LIVE") || normalized.contains("WEBINAR");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listScheduledSessions(UUID tenantId, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, course_id, cohort_id, title, session_type, starts_at, ends_at, facilitator, location_ref, metadata_json, created_at
                from lrn_scheduled_learning_session
                where tenant_id = ?
                order by starts_at asc
                limit ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class).toString());
                    row.put("courseId", rs.getObject("course_id", UUID.class).toString());
                    row.put("cohortId", rs.getObject("cohort_id") == null ? null : rs.getObject("cohort_id", UUID.class).toString());
                    row.put("title", rs.getString("title"));
                    row.put("sessionType", rs.getString("session_type"));
                    row.put("startsAt", str(rs, "starts_at"));
                    row.put("endsAt", str(rs, "ends_at"));
                    row.put("facilitator", rs.getString("facilitator"));
                    row.put("locationRef", rs.getString("location_ref"));
                    row.put("metadata", readJson(rs.getString("metadata_json")));
                    row.put("createdAt", str(rs, "created_at"));
                    return row;
                },
                tenantId,
                limit);
        return Map.of("items", rows, "limit", limit);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studioDashboard(UUID tenantId) {
        int draftCourses = jdbcTemplate.queryForObject(
                "select count(*) from lrn_course where tenant_id = ? and status = 'DRAFT'",
                Integer.class,
                tenantId);
        int publishedCourses = jdbcTemplate.queryForObject(
                "select count(*) from lrn_course where tenant_id = ? and status = 'PUBLISHED'",
                Integer.class,
                tenantId);
        int aiPendingReview = jdbcTemplate.queryForObject(
                "select count(*) from lrn_ai_generation_draft where tenant_id = ? and review_status = 'PENDING_REVIEW'",
                Integer.class,
                tenantId);
        int resources = jdbcTemplate.queryForObject(
                "select count(*) from lrn_library_resource where tenant_id = ? and archived_at is null",
                Integer.class,
                tenantId);
        int mediaDrafts = jdbcTemplate.queryForObject(
                "select count(*) from lrn_media_asset where tenant_id = ? and status = 'DRAFT'",
                Integer.class,
                tenantId);
        return Map.of(
                "draftCourses", draftCourses,
                "publishedCourses", publishedCourses,
                "aiPendingReview", aiPendingReview,
                "libraryResources", resources,
                "mediaDrafts", mediaDrafts);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> courseReadiness(UUID tenantId, String courseId) {
        Integer moduleCount = jdbcTemplate.queryForObject(
                "select count(*) from lrn_course_module where tenant_id = ? and course_id = ?",
                Integer.class,
                tenantId,
                UUID.fromString(courseId));
        Integer lessonCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from lrn_course_lesson l
                join lrn_course_module m on m.id = l.module_id
                where m.tenant_id = ? and m.course_id = ?
                """,
                Integer.class,
                tenantId,
                UUID.fromString(courseId));
        Integer assessmentCount = jdbcTemplate.queryForObject(
                "select count(*) from lrn_assessment where tenant_id = ? and course_id = ?",
                Integer.class,
                tenantId,
                UUID.fromString(courseId));
        List<Map<String, Object>> checklist = List.of(
                Map.of("item", "Course metadata", "ok", true),
                Map.of("item", "At least one module", "ok", moduleCount != null && moduleCount > 0),
                Map.of("item", "At least one lesson", "ok", lessonCount != null && lessonCount > 0),
                Map.of("item", "Assessment or survey", "ok", assessmentCount != null && assessmentCount > 0),
                Map.of("item", "Human review", "ok", true));
        return Map.of(
                "courseId", courseId,
                "moduleCount", moduleCount == null ? 0 : moduleCount,
                "lessonCount", lessonCount == null ? 0 : lessonCount,
                "assessmentCount", assessmentCount == null ? 0 : assessmentCount,
                "readinessChecklist", checklist,
                "publishReady", checklist.stream().allMatch(i -> Boolean.TRUE.equals(i.get("ok"))));
    }

    @Transactional
    public Map<String, Object> nompiloAssist(Map<String, Object> body) {
        return Map.of(
                "assistant", "Nompilo",
                "mode", "FundoAssistantStub",
                "response", "I can help explain content, draft quizzes, and suggest next steps. Real provider integration is pluggable.",
                "context", body);
    }

    private List<Map<String, Object>> listLessons(UUID moduleId) {
        return jdbcTemplate.query(
                """
                select id, title, content_type, content_body, content_ref, sequence_no, required, status, content_blocks_json
                from lrn_course_lesson
                where module_id = ?
                order by sequence_no asc
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class).toString());
                    row.put("title", rs.getString("title"));
                    row.put("contentType", rs.getString("content_type"));
                    row.put("contentBody", rs.getString("content_body"));
                    row.put("contentRef", rs.getString("content_ref"));
                    row.put("sequenceNo", rs.getInt("sequence_no"));
                    row.put("required", rs.getBoolean("required"));
                    row.put("status", rs.getString("status"));
                    row.put("contentBlocks", readJson(rs.getString("content_blocks_json")));
                    return row;
                },
                moduleId);
    }

    private Map<String, Object> mapCourseRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getObject("id", UUID.class).toString());
        row.put("courseId", rs.getObject("id", UUID.class).toString());
        row.put("code", rs.getString("code"));
        row.put("title", rs.getString("title"));
        row.put("description", rs.getString("description"));
        row.put("category", rs.getString("category"));
        row.put("level", rs.getString("level"));
        row.put("status", rs.getString("status"));
        row.put("language", rs.getString("language"));
        row.put("estimatedDurationMinutes", rs.getObject("estimated_duration_minutes"));
        row.put("mandatory", rs.getBoolean("mandatory"));
        row.put("cpdEligible", rs.getBoolean("cpd_eligible"));
        return row;
    }

    private int nextSequence(String table, String parentField, String parentId) {
        Integer next = jdbcTemplate.queryForObject(
                "select coalesce(max(sequence_no), 0) + 1 from " + table + " where " + parentField + " = ?",
                Integer.class,
                UUID.fromString(parentId));
        return next == null ? 1 : next;
    }

    private int nextQuestionSequence(String assessmentId) {
        Integer next = jdbcTemplate.queryForObject(
                "select coalesce(max(sequence_no), 0) + 1 from lrn_assessment_question where assessment_id = ?",
                Integer.class,
                UUID.fromString(assessmentId));
        return next == null ? 1 : next;
    }

    private int nextAttemptNo(String assessmentId, String subjectType, String subjectId) {
        Integer next = jdbcTemplate.queryForObject(
                """
                select coalesce(max(attempt_no), 0) + 1
                from lrn_assessment_attempt
                where assessment_id = ? and subject_type = ? and subject_id = ?
                """,
                Integer.class,
                UUID.fromString(assessmentId),
                subjectType,
                subjectId);
        return next == null ? 1 : next;
    }

    private void appendOutboxEvent(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        LearningOutboxEntity outbox = new LearningOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayloadJson(writeJson(payload));
        outboxRepository.save(outbox);
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String stringVal(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key);
        if (value == null) return fallback;
        String out = value.toString().trim();
        return out.isBlank() ? fallback : out;
    }

    private static String nullableString(Object value) {
        if (value == null) return null;
        String out = value.toString().trim();
        return out.isBlank() ? null : out;
    }

    private static boolean boolVal(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return fallback;
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Integer nullableInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private static Boolean nullableBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }

    private static UUID nullableUuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private static OffsetDateTime parseOffsetNullable(Object value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private static OffsetDateTime parseOffsetRequired(Object value) {
        OffsetDateTime parsed = parseOffsetNullable(value);
        return parsed != null ? parsed : OffsetDateTime.now();
    }

    private static String str(ResultSet rs, String column) throws SQLException {
        OffsetDateTime dt = rs.getObject(column, OffsetDateTime.class);
        return dt == null ? null : dt.toString();
    }
}
