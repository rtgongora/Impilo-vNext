package zw.gov.mohcc.impilo.learning.fundo;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentQuestionEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseLessonEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseModuleEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoPathwayEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoPathwayItemEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssessmentQuestionRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssessmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseLessonRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseModuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.FundoPathwayItemRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.FundoPathwayRepository;

/**
 * Phase 6A — native Fundo authoring write surface. Owns create/update for
 * course, module, lesson, pathway, pathway item, assessment and question
 * rows. Deliberately separate from the read-side Phase 5C services so the
 * authoring path can be evolved (workflow approvals, version pinning,
 * draft preview, etc.) without disturbing existing reads.
 *
 * <p>Emits the {@link FundoNativeEventTypes#COURSE_PUBLISHED} event type
 * (reserved in Phase 5D, finally emitted here) on a {@code DRAFT → PUBLISHED}
 * status transition on a course. No legacy event type counterpart exists,
 * so the single v1.1 emission is sufficient.</p>
 *
 * <p>This service is standalone-capable: it never invokes Moodle or any
 * other external LMS — it only writes to native {@code lrn_course*} /
 * {@code lrn_assessment*} / {@code lrn_fundo_pathway*} tables introduced
 * in Phase 5A's V006 migration.</p>
 */
@Service
public class FundoAuthoringService {

    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final CourseLessonRepository lessonRepository;
    private final FundoPathwayRepository pathwayRepository;
    private final FundoPathwayItemRepository pathwayItemRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final FundoOutboxAppender outbox;

    public FundoAuthoringService(
            CourseRepository courseRepository,
            CourseModuleRepository moduleRepository,
            CourseLessonRepository lessonRepository,
            FundoPathwayRepository pathwayRepository,
            FundoPathwayItemRepository pathwayItemRepository,
            AssessmentRepository assessmentRepository,
            AssessmentQuestionRepository questionRepository,
            FundoOutboxAppender outbox) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.pathwayRepository = pathwayRepository;
        this.pathwayItemRepository = pathwayItemRepository;
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.outbox = outbox;
    }

    // ───────────────────────── Course ─────────────────────────

    @Transactional
    public AuthoringResult<Map<String, Object>> createCourse(UUID tenantId, CourseUpsert req) {
        if (req == null || isBlank(req.code()) || isBlank(req.title())) {
            return AuthoringResult.badRequest("BAD_REQUEST", "code and title are required");
        }
        Optional<CourseEntity> existing = courseRepository.findByTenantIdAndCode(tenantId, req.code());
        if (existing.isPresent()) {
            return AuthoringResult.conflict("COURSE_CODE_TAKEN", "Course code already exists");
        }
        CourseEntity c = new CourseEntity();
        c.setTenantId(tenantId);
        c.setCode(req.code());
        c.setTitle(req.title());
        applyCourseFields(c, req);
        if (isBlank(c.getStatus())) c.setStatus("DRAFT");
        courseRepository.save(c);
        maybeEmitCoursePublished(c, /* oldStatus */ null);
        return AuthoringResult.ok(FundoCatalogService.toView(c));
    }

    @Transactional
    public AuthoringResult<Map<String, Object>> updateCourse(UUID tenantId, UUID courseId, CourseUpsert req) {
        Optional<CourseEntity> row = courseRepository.findById(courseId)
                .filter(c -> c.getTenantId().equals(tenantId));
        if (row.isEmpty()) return AuthoringResult.notFound("COURSE_NOT_FOUND", "Course not found");
        CourseEntity c = row.get();
        String oldStatus = c.getStatus();
        if (req.code() != null && !req.code().equals(c.getCode())) {
            Optional<CourseEntity> clash = courseRepository.findByTenantIdAndCode(tenantId, req.code());
            if (clash.isPresent() && !clash.get().getId().equals(c.getId())) {
                return AuthoringResult.conflict("COURSE_CODE_TAKEN", "Course code already exists");
            }
            c.setCode(req.code());
        }
        if (req.title() != null) c.setTitle(req.title());
        applyCourseFields(c, req);
        courseRepository.save(c);
        maybeEmitCoursePublished(c, oldStatus);
        return AuthoringResult.ok(FundoCatalogService.toView(c));
    }

    private void applyCourseFields(CourseEntity c, CourseUpsert req) {
        if (req.description() != null) c.setDescription(req.description());
        if (req.category() != null) c.setCategory(req.category());
        if (req.level() != null) c.setLevel(req.level());
        if (req.status() != null) c.setStatus(req.status());
        if (req.language() != null) c.setLanguage(req.language());
        if (req.estimatedDurationMinutes() != null) c.setEstimatedDurationMinutes(req.estimatedDurationMinutes());
        if (req.mandatory() != null) c.setMandatory(req.mandatory());
        if (req.cpdEligible() != null) c.setCpdEligible(req.cpdEligible());
        if (req.cpdPoints() != null) c.setCpdPoints(req.cpdPoints());
        applyDueDate(c, req);
        applyAudience(c, req);
    }

    /**
     * V031. The two modes are mutually exclusive and each requires its own companion value.
     * Validated here so a bad request is a 400 with a reason rather than a constraint
     * violation surfacing as a 500 — the DB check remains the authority, this is the
     * readable failure in front of it. Switching mode clears the other mode's value, so a
     * FIXED→RELATIVE edit cannot leave a stale absolute date behind.
     */
    private void applyDueDate(CourseEntity c, CourseUpsert req) {
        if (req.dueDateType() == null) return;
        String type = req.dueDateType().trim().toUpperCase();
        switch (type) {
            case "NONE", "" -> {
                c.setDueDateType(null);
                c.setDueDate(null);
                c.setDueDateDaysFromEnrollment(null);
            }
            case "FIXED" -> {
                if (req.dueDate() == null) {
                    throw new IllegalArgumentException("dueDate is required when dueDateType is FIXED");
                }
                c.setDueDateType("FIXED");
                c.setDueDate(req.dueDate());
                c.setDueDateDaysFromEnrollment(null);
            }
            case "RELATIVE" -> {
                Integer days = req.dueDateDaysFromEnrollment();
                if (days == null || days < 0) {
                    throw new IllegalArgumentException(
                            "dueDateDaysFromEnrollment must be a non-negative number when dueDateType is RELATIVE");
                }
                c.setDueDateType("RELATIVE");
                c.setDueDateDaysFromEnrollment(days);
                c.setDueDate(null);
            }
            default -> throw new IllegalArgumentException(
                    "dueDateType must be one of FIXED, RELATIVE, NONE (got: " + req.dueDateType() + ")");
        }
    }

    /**
     * V032. Roles are only meaningful for SPECIFIC_ROLES; every other mode must store NULL,
     * otherwise chk_lrn_course_audience_roles rejects the row. Narrowing to SPECIFIC_ROLES
     * without naming a role is refused rather than silently targeting nobody.
     */
    private void applyAudience(CourseEntity c, CourseUpsert req) {
        if (req.audienceType() == null) return;
        String type = req.audienceType().trim().toUpperCase();
        if (!AUDIENCE_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "audienceType must be one of " + AUDIENCE_TYPES + " (got: " + req.audienceType() + ")");
        }
        if ("SPECIFIC_ROLES".equals(type)) {
            String roles = req.audienceRoles() == null ? "" : req.audienceRoles().trim();
            if (roles.isEmpty()) {
                throw new IllegalArgumentException(
                        "audienceRoles is required when audienceType is SPECIFIC_ROLES");
            }
            c.setAudienceType(type);
            c.setAudienceRoles(roles);
        } else {
            c.setAudienceType(type);
            c.setAudienceRoles(null);
        }
    }

    private static final java.util.Set<String> AUDIENCE_TYPES =
            java.util.Set.of("ALL_LEARNERS", "SYSTEM_USERS", "CITIZENS", "SPECIFIC_ROLES");

    private void maybeEmitCoursePublished(CourseEntity c, String oldStatus) {
        if ("PUBLISHED".equals(c.getStatus()) && !"PUBLISHED".equals(oldStatus)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tenantId", c.getTenantId().toString());
            payload.put("courseId", c.getId().toString());
            payload.put("courseCode", c.getCode());
            payload.put("title", c.getTitle());
            payload.put("cpdEligible", c.isCpdEligible());
            payload.put("mandatory", c.isMandatory());
            payload.put("version", c.getVersion());
            outbox.append("FundoCourse", c.getId().toString(),
                    FundoNativeEventTypes.COURSE_PUBLISHED, payload);
        }
    }

    // ───────────────────────── Module ─────────────────────────

    @Transactional
    public AuthoringResult<Map<String, Object>> createModule(UUID tenantId, UUID courseId, ModuleUpsert req) {
        Optional<CourseEntity> course = courseRepository.findById(courseId)
                .filter(c -> c.getTenantId().equals(tenantId));
        if (course.isEmpty()) return AuthoringResult.notFound("COURSE_NOT_FOUND", "Course not found");
        if (req == null || isBlank(req.title())) {
            return AuthoringResult.badRequest("BAD_REQUEST", "title is required");
        }
        int sequence = req.sequence() != null ? req.sequence() : nextModuleSequence(courseId);
        if (moduleSequenceTaken(courseId, sequence)) {
            return AuthoringResult.conflict("MODULE_SEQUENCE_TAKEN", "Module sequence already used");
        }
        CourseModuleEntity m = new CourseModuleEntity();
        m.setTenantId(tenantId);
        m.setCourseId(courseId);
        m.setTitle(req.title());
        m.setDescription(req.description());
        m.setSequenceNo(sequence);
        m.setStatus(isBlank(req.status()) ? "PUBLISHED" : req.status());
        moduleRepository.save(m);
        return AuthoringResult.ok(toModuleView(m));
    }

    @Transactional
    public AuthoringResult<Map<String, Object>> updateModule(UUID tenantId, UUID moduleId, ModuleUpsert req) {
        Optional<CourseModuleEntity> row = moduleRepository.findById(moduleId)
                .filter(m -> m.getTenantId().equals(tenantId));
        if (row.isEmpty()) return AuthoringResult.notFound("MODULE_NOT_FOUND", "Module not found");
        CourseModuleEntity m = row.get();
        if (req.title() != null) m.setTitle(req.title());
        if (req.description() != null) m.setDescription(req.description());
        if (req.status() != null) m.setStatus(req.status());
        if (req.sequence() != null && req.sequence() != m.getSequenceNo()) {
            if (moduleSequenceTaken(m.getCourseId(), req.sequence())) {
                return AuthoringResult.conflict("MODULE_SEQUENCE_TAKEN", "Module sequence already used");
            }
            m.setSequenceNo(req.sequence());
        }
        moduleRepository.save(m);
        return AuthoringResult.ok(toModuleView(m));
    }

    private int nextModuleSequence(UUID courseId) {
        List<CourseModuleEntity> existing = moduleRepository.findByCourseIdOrderBySequenceNoAsc(courseId);
        return existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getSequenceNo() + 1;
    }

    private boolean moduleSequenceTaken(UUID courseId, int seq) {
        return moduleRepository.findByCourseIdOrderBySequenceNoAsc(courseId).stream()
                .anyMatch(m -> m.getSequenceNo() == seq);
    }

    private static Map<String, Object> toModuleView(CourseModuleEntity m) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId().toString());
        v.put("courseId", m.getCourseId().toString());
        v.put("title", m.getTitle());
        v.put("description", m.getDescription());
        v.put("sequence", m.getSequenceNo());
        v.put("status", m.getStatus());
        return v;
    }

    // ───────────────────────── Lesson ─────────────────────────

    @Transactional
    public AuthoringResult<Map<String, Object>> createLesson(UUID tenantId, UUID moduleId, LessonUpsert req) {
        Optional<CourseModuleEntity> module = moduleRepository.findById(moduleId)
                .filter(m -> m.getTenantId().equals(tenantId));
        if (module.isEmpty()) return AuthoringResult.notFound("MODULE_NOT_FOUND", "Module not found");
        if (req == null || isBlank(req.title()) || isBlank(req.contentType())) {
            return AuthoringResult.badRequest("BAD_REQUEST", "title and contentType are required");
        }
        int sequence = req.sequence() != null ? req.sequence() : nextLessonSequence(moduleId);
        if (lessonSequenceTaken(moduleId, sequence)) {
            return AuthoringResult.conflict("LESSON_SEQUENCE_TAKEN", "Lesson sequence already used");
        }
        CourseLessonEntity l = new CourseLessonEntity();
        l.setTenantId(tenantId);
        l.setModuleId(moduleId);
        l.setTitle(req.title());
        l.setContentType(req.contentType());
        l.setContentBody(req.contentBody());
        l.setContentRef(req.contentRef());
        l.setContentFormat(isBlank(req.contentFormat()) ? "PLAIN_TEXT" : req.contentFormat());
        l.setContentBlocksJson(req.contentBlocksJson());
        l.setEstimatedDurationMinutes(req.estimatedDurationMinutes());
        l.setSequenceNo(sequence);
        l.setRequired(req.required() == null ? true : req.required());
        l.setStatus(isBlank(req.status()) ? "PUBLISHED" : req.status());
        lessonRepository.save(l);
        return AuthoringResult.ok(FundoCourseStructureService.toLessonView(l));
    }

    @Transactional
    public AuthoringResult<Map<String, Object>> updateLesson(UUID tenantId, UUID lessonId, LessonUpsert req) {
        Optional<CourseLessonEntity> row = lessonRepository.findById(lessonId)
                .filter(l -> l.getTenantId().equals(tenantId));
        if (row.isEmpty()) return AuthoringResult.notFound("LESSON_NOT_FOUND", "Lesson not found");
        CourseLessonEntity l = row.get();
        if (req.title() != null) l.setTitle(req.title());
        if (req.contentType() != null) l.setContentType(req.contentType());
        if (req.contentBody() != null) l.setContentBody(req.contentBody());
        if (req.contentRef() != null) l.setContentRef(req.contentRef());
        if (req.contentFormat() != null) l.setContentFormat(req.contentFormat());
        if (req.contentBlocksJson() != null) l.setContentBlocksJson(req.contentBlocksJson());
        if (req.estimatedDurationMinutes() != null) l.setEstimatedDurationMinutes(req.estimatedDurationMinutes());
        if (req.required() != null) l.setRequired(req.required());
        if (req.status() != null) l.setStatus(req.status());
        if (req.sequence() != null && req.sequence() != l.getSequenceNo()) {
            if (lessonSequenceTaken(l.getModuleId(), req.sequence())) {
                return AuthoringResult.conflict("LESSON_SEQUENCE_TAKEN", "Lesson sequence already used");
            }
            l.setSequenceNo(req.sequence());
        }
        lessonRepository.save(l);
        return AuthoringResult.ok(FundoCourseStructureService.toLessonView(l));
    }

    private int nextLessonSequence(UUID moduleId) {
        List<CourseLessonEntity> existing = lessonRepository.findByModuleIdOrderBySequenceNoAsc(moduleId);
        return existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getSequenceNo() + 1;
    }

    private boolean lessonSequenceTaken(UUID moduleId, int seq) {
        return lessonRepository.findByModuleIdOrderBySequenceNoAsc(moduleId).stream()
                .anyMatch(l -> l.getSequenceNo() == seq);
    }

    // ───────────────────────── Pathway ─────────────────────────

    @Transactional
    public AuthoringResult<Map<String, Object>> createPathway(UUID tenantId, PathwayUpsert req) {
        if (req == null || isBlank(req.code()) || isBlank(req.title())) {
            return AuthoringResult.badRequest("BAD_REQUEST", "code and title are required");
        }
        Optional<FundoPathwayEntity> existing = pathwayRepository.findByTenantIdAndCode(tenantId, req.code());
        if (existing.isPresent()) {
            return AuthoringResult.conflict("PATHWAY_CODE_TAKEN", "Pathway code already exists");
        }
        FundoPathwayEntity p = new FundoPathwayEntity();
        p.setTenantId(tenantId);
        p.setCode(req.code());
        p.setTitle(req.title());
        applyPathwayFields(p, req);
        if (isBlank(p.getStatus())) p.setStatus("DRAFT");
        pathwayRepository.save(p);
        return AuthoringResult.ok(FundoPathwayService.toPathwayView(p));
    }

    @Transactional
    public AuthoringResult<Map<String, Object>> updatePathway(UUID tenantId, UUID pathwayId, PathwayUpsert req) {
        Optional<FundoPathwayEntity> row = pathwayRepository.findById(pathwayId)
                .filter(p -> p.getTenantId().equals(tenantId));
        if (row.isEmpty()) return AuthoringResult.notFound("PATHWAY_NOT_FOUND", "Pathway not found");
        FundoPathwayEntity p = row.get();
        if (req.code() != null && !req.code().equals(p.getCode())) {
            Optional<FundoPathwayEntity> clash = pathwayRepository.findByTenantIdAndCode(tenantId, req.code());
            if (clash.isPresent() && !clash.get().getId().equals(p.getId())) {
                return AuthoringResult.conflict("PATHWAY_CODE_TAKEN", "Pathway code already exists");
            }
            p.setCode(req.code());
        }
        if (req.title() != null) p.setTitle(req.title());
        applyPathwayFields(p, req);
        pathwayRepository.save(p);
        return AuthoringResult.ok(FundoPathwayService.toPathwayView(p));
    }

    private void applyPathwayFields(FundoPathwayEntity p, PathwayUpsert req) {
        if (req.description() != null) p.setDescription(req.description());
        if (req.targetCadres() != null) p.setTargetCadres(req.targetCadres());
        if (req.targetRoles() != null) p.setTargetRoles(req.targetRoles());
        if (req.targetFacilityLevels() != null) p.setTargetFacilityLevels(req.targetFacilityLevels());
        if (req.status() != null) p.setStatus(req.status());
    }

    @Transactional
    public AuthoringResult<Map<String, Object>> addPathwayItem(UUID tenantId, UUID pathwayId, PathwayItemUpsert req) {
        Optional<FundoPathwayEntity> pathway = pathwayRepository.findById(pathwayId)
                .filter(p -> p.getTenantId().equals(tenantId));
        if (pathway.isEmpty()) return AuthoringResult.notFound("PATHWAY_NOT_FOUND", "Pathway not found");
        if (req == null || req.courseId() == null) {
            return AuthoringResult.badRequest("BAD_REQUEST", "courseId is required");
        }
        // Verify course is in same tenant.
        Optional<CourseEntity> course = courseRepository.findById(req.courseId())
                .filter(c -> c.getTenantId().equals(tenantId));
        if (course.isEmpty()) return AuthoringResult.notFound("COURSE_NOT_FOUND", "Course not found in tenant");

        int sequence = req.sequence() != null
                ? req.sequence()
                : nextPathwayItemSequence(pathwayId);
        if (pathwayItemSequenceTaken(pathwayId, sequence)) {
            return AuthoringResult.conflict("PATHWAY_ITEM_SEQUENCE_TAKEN", "Pathway item sequence already used");
        }
        FundoPathwayItemEntity it = new FundoPathwayItemEntity();
        it.setPathwayId(pathwayId);
        it.setCourseId(req.courseId());
        it.setSequenceNo(sequence);
        it.setRequired(req.required() == null ? true : req.required());
        it.setPrerequisiteCourseId(req.prerequisiteCourseId());
        pathwayItemRepository.save(it);

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", it.getId().toString());
        v.put("pathwayId", pathwayId.toString());
        v.put("courseId", req.courseId().toString());
        v.put("sequence", sequence);
        v.put("required", it.isRequired());
        v.put("prerequisiteCourseId",
                it.getPrerequisiteCourseId() == null ? null : it.getPrerequisiteCourseId().toString());
        return AuthoringResult.ok(v);
    }

    private int nextPathwayItemSequence(UUID pathwayId) {
        List<FundoPathwayItemEntity> items = pathwayItemRepository.findByPathwayIdOrderBySequenceNoAsc(pathwayId);
        return items.isEmpty() ? 1 : items.get(items.size() - 1).getSequenceNo() + 1;
    }

    private boolean pathwayItemSequenceTaken(UUID pathwayId, int seq) {
        return pathwayItemRepository.findByPathwayIdOrderBySequenceNoAsc(pathwayId).stream()
                .anyMatch(i -> i.getSequenceNo() == seq);
    }

    // ───────────────────────── Assessment + Question ─────────────────────────

    @Transactional
    public AuthoringResult<Map<String, Object>> createAssessment(UUID tenantId, AssessmentUpsert req) {
        if (req == null || req.courseId() == null || isBlank(req.title()) || isBlank(req.assessmentType())) {
            return AuthoringResult.badRequest("BAD_REQUEST", "courseId, title and assessmentType are required");
        }
        Optional<CourseEntity> course = courseRepository.findById(req.courseId())
                .filter(c -> c.getTenantId().equals(tenantId));
        if (course.isEmpty()) return AuthoringResult.notFound("COURSE_NOT_FOUND", "Course not found in tenant");

        AssessmentEntity a = new AssessmentEntity();
        a.setTenantId(tenantId);
        a.setCourseId(req.courseId());
        a.setModuleId(req.moduleId());
        a.setTitle(req.title());
        a.setDescription(req.description());
        a.setAssessmentType(req.assessmentType());
        if (req.passMark() != null) a.setPassMark(req.passMark());
        if (req.maxAttempts() != null) a.setMaxAttempts(req.maxAttempts());
        if (req.status() != null) a.setStatus(req.status());
        assessmentRepository.save(a);
        return AuthoringResult.ok(toAssessmentView(a));
    }

    @Transactional
    public AuthoringResult<Map<String, Object>> updateAssessment(UUID tenantId, UUID assessmentId, AssessmentUpsert req) {
        Optional<AssessmentEntity> row = assessmentRepository.findByTenantIdAndId(tenantId, assessmentId);
        if (row.isEmpty()) return AuthoringResult.notFound("ASSESSMENT_NOT_FOUND", "Assessment not found");
        AssessmentEntity a = row.get();
        if (req.title() != null) a.setTitle(req.title());
        if (req.description() != null) a.setDescription(req.description());
        if (req.assessmentType() != null) a.setAssessmentType(req.assessmentType());
        if (req.passMark() != null) a.setPassMark(req.passMark());
        if (req.maxAttempts() != null) a.setMaxAttempts(req.maxAttempts());
        if (req.status() != null) a.setStatus(req.status());
        if (req.moduleId() != null) a.setModuleId(req.moduleId());
        assessmentRepository.save(a);
        return AuthoringResult.ok(toAssessmentView(a));
    }

    @Transactional
    public AuthoringResult<Map<String, Object>> addQuestion(UUID tenantId, UUID assessmentId, QuestionUpsert req) {
        Optional<AssessmentEntity> assessment = assessmentRepository.findByTenantIdAndId(tenantId, assessmentId);
        if (assessment.isEmpty()) return AuthoringResult.notFound("ASSESSMENT_NOT_FOUND", "Assessment not found");
        if (req == null || isBlank(req.questionType()) || isBlank(req.prompt())) {
            return AuthoringResult.badRequest("BAD_REQUEST", "questionType and prompt are required");
        }
        int sequence = req.sequence() != null ? req.sequence() : nextQuestionSequence(assessmentId);
        if (questionSequenceTaken(assessmentId, sequence)) {
            return AuthoringResult.conflict("QUESTION_SEQUENCE_TAKEN", "Question sequence already used");
        }
        AssessmentQuestionEntity q = new AssessmentQuestionEntity();
        q.setAssessmentId(assessmentId);
        q.setQuestionType(req.questionType());
        q.setPrompt(req.prompt());
        q.setOptionsJson(req.optionsJson());
        q.setCorrectAnswerJson(req.correctAnswerJson());
        q.setRubricJson(req.rubricJson());
        q.setSequenceNo(sequence);
        q.setPoints(req.points() == null ? 1 : req.points());
        questionRepository.save(q);

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", q.getId().toString());
        v.put("assessmentId", assessmentId.toString());
        v.put("questionType", q.getQuestionType());
        v.put("prompt", q.getPrompt());
        v.put("sequence", q.getSequenceNo());
        v.put("points", q.getPoints());
        return AuthoringResult.ok(v);
    }

    @Transactional
    public AuthoringResult<Map<String, Object>> updateQuestion(
            UUID tenantId, UUID assessmentId, UUID questionId, QuestionUpsert req) {
        Optional<AssessmentEntity> assessment = assessmentRepository.findByTenantIdAndId(tenantId, assessmentId);
        if (assessment.isEmpty()) return AuthoringResult.notFound("ASSESSMENT_NOT_FOUND", "Assessment not found");
        Optional<AssessmentQuestionEntity> row = questionRepository.findById(questionId)
                .filter(q -> q.getAssessmentId().equals(assessmentId));
        if (row.isEmpty()) return AuthoringResult.notFound("QUESTION_NOT_FOUND", "Question not found");
        if (req == null) return AuthoringResult.badRequest("BAD_REQUEST", "Question payload is required");

        AssessmentQuestionEntity q = row.get();
        if (req.questionType() != null) q.setQuestionType(req.questionType());
        if (req.prompt() != null) q.setPrompt(req.prompt());
        if (req.optionsJson() != null) q.setOptionsJson(req.optionsJson());
        if (req.correctAnswerJson() != null) q.setCorrectAnswerJson(req.correctAnswerJson());
        if (req.rubricJson() != null) q.setRubricJson(req.rubricJson());
        if (req.points() != null) q.setPoints(req.points());
        if (req.sequence() != null && req.sequence() != q.getSequenceNo()) {
            if (questionSequenceTaken(assessmentId, req.sequence())) {
                return AuthoringResult.conflict("QUESTION_SEQUENCE_TAKEN", "Question sequence already used");
            }
            q.setSequenceNo(req.sequence());
        }
        questionRepository.save(q);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", q.getId().toString());
        v.put("assessmentId", assessmentId.toString());
        v.put("questionType", q.getQuestionType());
        v.put("prompt", q.getPrompt());
        v.put("sequence", q.getSequenceNo());
        v.put("points", q.getPoints());
        return AuthoringResult.ok(v);
    }

    private int nextQuestionSequence(UUID assessmentId) {
        List<AssessmentQuestionEntity> items = questionRepository.findByAssessmentIdOrderBySequenceNoAsc(assessmentId);
        return items.isEmpty() ? 1 : items.get(items.size() - 1).getSequenceNo() + 1;
    }

    private boolean questionSequenceTaken(UUID assessmentId, int seq) {
        return questionRepository.findByAssessmentIdOrderBySequenceNoAsc(assessmentId).stream()
                .anyMatch(q -> q.getSequenceNo() == seq);
    }

    private static Map<String, Object> toAssessmentView(AssessmentEntity a) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", a.getId().toString());
        v.put("courseId", a.getCourseId().toString());
        v.put("moduleId", a.getModuleId() == null ? null : a.getModuleId().toString());
        v.put("title", a.getTitle());
        v.put("description", a.getDescription());
        v.put("assessmentType", a.getAssessmentType());
        v.put("passMark", a.getPassMark());
        v.put("maxAttempts", a.getMaxAttempts());
        v.put("status", a.getStatus());
        return v;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ───────────────────────── DTOs ─────────────────────────

    /**
     * Course create/update payload. All fields except {@code code} and {@code title}
     * are nullable so the same record can be used as a full create or a partial
     * patch on update.
     */
    public record CourseUpsert(
            String code,
            String title,
            String description,
            String category,
            String level,
            String status,
            String language,
            Integer estimatedDurationMinutes,
            Boolean mandatory,
            Boolean cpdEligible,
            Integer cpdPoints,
            // V031/V032. Appended, never reordered — this record is deserialised from the
            // authoring request body, so existing callers that omit them keep working and
            // simply leave the course with no deadline and ALL_LEARNERS targeting.
            String dueDateType,
            OffsetDateTime dueDate,
            Integer dueDateDaysFromEnrollment,
            String audienceType,
            String audienceRoles) {

        /**
         * An all-null upsert — every field absent, so applyCourseFields changes nothing.
         * Callers used a positional constructor of nulls, which silently breaks each time a
         * field is appended; this keeps that failure impossible.
         */
        public static CourseUpsert empty() {
            return new CourseUpsert(null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        }
    }

    public record ModuleUpsert(
            String title,
            String description,
            Integer sequence,
            String status) {}

    public record LessonUpsert(
            String title,
            String contentType,
            String contentBody,
            String contentRef,
            String contentFormat,
            String contentBlocksJson,
            Integer estimatedDurationMinutes,
            Integer sequence,
            Boolean required,
            String status) {}

    public record PathwayUpsert(
            String code,
            String title,
            String description,
            String targetCadres,
            String targetRoles,
            String targetFacilityLevels,
            String status) {}

    public record PathwayItemUpsert(
            UUID courseId,
            Integer sequence,
            Boolean required,
            UUID prerequisiteCourseId) {}

    public record AssessmentUpsert(
            UUID courseId,
            UUID moduleId,
            String title,
            String description,
            String assessmentType,
            Integer passMark,
            Integer maxAttempts,
            String status) {}

    public record QuestionUpsert(
            String questionType,
            String prompt,
            String optionsJson,
            String correctAnswerJson,
            String rubricJson,
            Integer sequence,
            Integer points) {}

    /**
     * Lightweight result type — keeps controllers decoupled from
     * Spring's exception machinery while still allowing distinct 400 / 404 /
     * 409 outcomes to be mapped to the v1.1 envelope.
     */
    public static final class AuthoringResult<T> {
        public enum Kind { OK, BAD_REQUEST, NOT_FOUND, CONFLICT }

        private final Kind kind;
        private final T value;
        private final String code;
        private final String message;

        private AuthoringResult(Kind kind, T value, String code, String message) {
            this.kind = kind;
            this.value = value;
            this.code = code;
            this.message = message;
        }

        public static <T> AuthoringResult<T> ok(T value) {
            return new AuthoringResult<>(Kind.OK, value, null, null);
        }

        public static <T> AuthoringResult<T> badRequest(String code, String message) {
            return new AuthoringResult<>(Kind.BAD_REQUEST, null, code, message);
        }

        public static <T> AuthoringResult<T> notFound(String code, String message) {
            return new AuthoringResult<>(Kind.NOT_FOUND, null, code, message);
        }

        public static <T> AuthoringResult<T> conflict(String code, String message) {
            return new AuthoringResult<>(Kind.CONFLICT, null, code, message);
        }

        public Kind kind() { return kind; }
        public T value() { return value; }
        public String code() { return code; }
        public String message() { return message; }
        public boolean isOk() { return kind == Kind.OK; }
    }
}
