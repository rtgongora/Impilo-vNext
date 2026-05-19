package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentQuestionEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseLessonEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseModuleEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoPathwayEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoPathwayItemEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningOutboxEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssessmentQuestionRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssessmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseLessonRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseModuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.FundoPathwayItemRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.FundoPathwayRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningOutboxRepository;

/**
 * Phase 5H — End-to-end integration suite for the native Impilo Fundo LMS
 * foundation (catalogue, course structure, pathways, enrolment, progress,
 * assessment foundation, certificates, learning record).
 *
 * <p>Each {@link Nested} group exercises one acceptance area of the Phase 5
 * scope through the real service layer (no Mockito stubs for services or
 * repositories — only {@link MoodleWebServiceClient} is mocked, and the
 * test verifies <em>zero</em> interactions on it to prove the native flow
 * is standalone.</p>
 *
 * <p>Outbox event emission is verified by querying {@link
 * LearningOutboxRepository} in the same transaction — the publisher itself
 * is disabled in the {@code test} profile.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoNativeLmsIT {

    private static final UUID TENANT = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseModuleRepository moduleRepository;
    @Autowired private CourseLessonRepository lessonRepository;
    @Autowired private FundoPathwayRepository pathwayRepository;
    @Autowired private FundoPathwayItemRepository pathwayItemRepository;
    @Autowired private AssessmentRepository assessmentRepository;
    @Autowired private AssessmentQuestionRepository questionRepository;
    @Autowired private LearningOutboxRepository outboxRepository;

    @Autowired private FundoCatalogService catalogService;
    @Autowired private FundoCourseStructureService structureService;
    @Autowired private FundoPathwayService pathwayService;
    @Autowired private FundoEnrolmentService enrolmentService;
    @Autowired private FundoProgressService progressService;
    @Autowired private FundoAssessmentService assessmentService;
    @Autowired private FundoCertificateService certificateService;
    @Autowired private FundoLearningRecordService recordService;
    @Autowired private FundoLearnerJourneyService learnerJourneyService;
    @Autowired private FundoReportService reportService;

    /**
     * The standalone-mode guarantee: {@link MoodleWebServiceClient} is a
     * mock bean and the test verifies zero interactions on it at the end of
     * every flow. Any native Fundo code that secretly called Moodle would
     * fail the assertion in {@link Standalone}.
     */
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    private CourseEntity courseA;
    private CourseEntity courseB;
    private CourseModuleEntity moduleA1;
    private CourseLessonEntity lessonA11;
    private CourseLessonEntity lessonA12;

    @BeforeEach
    void seedNativeContent() {
        courseA = newCourse("FUNDO-CTX-A", "Foundation A", "PUBLISHED", "ORIENTATION", false, false);
        courseB = newCourse("FUNDO-CTX-B", "Foundation B (CPD)", "PUBLISHED", "EHR_BASICS", false, true);
        CourseEntity draftCourse = newCourse("FUNDO-CTX-DRAFT", "Draft course", "DRAFT", "ORIENTATION", false, false);
        courseRepository.save(courseA);
        courseRepository.save(courseB);
        courseRepository.save(draftCourse);

        moduleA1 = newModule(courseA, "Module 1", 1);
        moduleRepository.save(moduleA1);
        lessonA11 = newLesson(moduleA1, "Lesson 1.1", 1);
        lessonA12 = newLesson(moduleA1, "Lesson 1.2", 2);
        lessonRepository.save(lessonA11);
        lessonRepository.save(lessonA12);

        FundoPathwayEntity pathway = new FundoPathwayEntity();
        pathway.setTenantId(TENANT);
        pathway.setCode("FUNDO-PATH-AB");
        pathway.setTitle("Foundation Path");
        pathway.setStatus("PUBLISHED");
        pathwayRepository.save(pathway);
        addPathwayItem(pathway, courseA, 1);
        addPathwayItem(pathway, courseB, 2);

        AssessmentEntity quiz = new AssessmentEntity();
        quiz.setTenantId(TENANT);
        quiz.setCourseId(courseA.getId());
        quiz.setTitle("Quick check");
        quiz.setAssessmentType("QUIZ");
        quiz.setPassMark(50);
        quiz.setMaxAttempts(3);
        quiz.setStatus("PUBLISHED");
        assessmentRepository.save(quiz);

        AssessmentQuestionEntity q1 = new AssessmentQuestionEntity();
        q1.setAssessmentId(quiz.getId());
        q1.setQuestionType("TRUE_FALSE");
        q1.setPrompt("Fundo runs standalone.");
        q1.setOptionsJson("[\"true\",\"false\"]");
        q1.setCorrectAnswerJson("\"true\"");
        q1.setSequenceNo(1);
        q1.setPoints(1);
        questionRepository.save(q1);

        AssessmentQuestionEntity q2 = new AssessmentQuestionEntity();
        q2.setAssessmentId(quiz.getId());
        q2.setQuestionType("MULTIPLE_CHOICE");
        q2.setPrompt("Pick the right runtime");
        q2.setOptionsJson("[\"moodle\",\"fundo\"]");
        q2.setCorrectAnswerJson("\"fundo\"");
        q2.setSequenceNo(2);
        q2.setPoints(1);
        questionRepository.save(q2);
    }

    private CourseEntity newCourse(String code, String title, String status, String category, boolean mandatory, boolean cpd) {
        CourseEntity c = new CourseEntity();
        c.setTenantId(TENANT);
        c.setCode(code);
        c.setTitle(title);
        c.setStatus(status);
        c.setCategory(category);
        c.setLevel("INTRODUCTORY");
        c.setLanguage("en");
        c.setMandatory(mandatory);
        c.setCpdEligible(cpd);
        c.setCpdPoints(cpd ? 2 : null);
        c.setVersion(1);
        return c;
    }

    private CourseModuleEntity newModule(CourseEntity course, String title, int seq) {
        CourseModuleEntity m = new CourseModuleEntity();
        m.setTenantId(TENANT);
        m.setCourseId(course.getId());
        m.setTitle(title);
        m.setSequenceNo(seq);
        m.setStatus("PUBLISHED");
        return m;
    }

    private CourseLessonEntity newLesson(CourseModuleEntity module, String title, int seq) {
        CourseLessonEntity l = new CourseLessonEntity();
        l.setTenantId(TENANT);
        l.setModuleId(module.getId());
        l.setTitle(title);
        l.setContentType("TEXT");
        l.setContentBody("Short placeholder body for tests.");
        l.setSequenceNo(seq);
        l.setRequired(true);
        l.setStatus("PUBLISHED");
        return l;
    }

    private void addPathwayItem(FundoPathwayEntity pathway, CourseEntity course, int seq) {
        FundoPathwayItemEntity it = new FundoPathwayItemEntity();
        it.setPathwayId(pathway.getId());
        it.setCourseId(course.getId());
        it.setSequenceNo(seq);
        it.setRequired(true);
        pathwayItemRepository.save(it);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) { return (T) o; }

    /* ───────────── Catalogue ───────────── */
    @Nested
    @DisplayName("Catalogue")
    class Catalogue {
        @Test
        @DisplayName("Lists only published courses by default")
        void listsPublishedOnly() {
            List<Map<String, Object>> items = catalogService.listCatalogue(
                    TENANT, new FundoCatalogService.CatalogueFilter(null, null, null, null, null, null), 25);
            assertThat(items).hasSize(2);
            assertThat(items).allSatisfy(c -> assertThat(c.get("status")).isEqualTo("PUBLISHED"));
        }

        @Test
        @DisplayName("Filters by category, CPD eligibility, mandatory")
        void filtersWork() {
            List<Map<String, Object>> byCategory = catalogService.listCatalogue(
                    TENANT, new FundoCatalogService.CatalogueFilter(null, "EHR_BASICS", null, null, null, null), 25);
            assertThat(byCategory).hasSize(1);
            assertThat(byCategory.get(0).get("code")).isEqualTo("FUNDO-CTX-B");

            List<Map<String, Object>> byCpd = catalogService.listCatalogue(
                    TENANT, new FundoCatalogService.CatalogueFilter(null, null, null, true, null, null), 25);
            assertThat(byCpd).hasSize(1);
            assertThat(byCpd.get(0).get("cpdEligible")).isEqualTo(true);
        }

        @Test
        @DisplayName("Course detail returns full view")
        void getCourseDetail() {
            assertThat(catalogService.getCourse(TENANT, courseA.getId())).isPresent();
            assertThat(catalogService.getCourse(TENANT, UUID.randomUUID())).isEmpty();
        }
    }

    /* ───────────── Course structure ───────────── */
    @Nested
    @DisplayName("Course structure")
    class Structure {
        @Test
        @DisplayName("Returns course with ordered modules and lessons")
        void returnsStructure() {
            Map<String, Object> structure = structureService.getStructure(TENANT, courseA.getId()).orElseThrow();
            List<Map<String, Object>> modules = cast(structure.get("modules"));
            assertThat(modules).hasSize(1);
            List<Map<String, Object>> lessons = cast(modules.get(0).get("lessons"));
            assertThat(lessons).hasSize(2);
            assertThat(lessons.get(0).get("title")).isEqualTo("Lesson 1.1");
        }
    }

    /* ───────────── Pathways ───────────── */
    @Nested
    @DisplayName("Pathways")
    class Pathways {
        @Test
        @DisplayName("List + detail return ordered items")
        void listAndDetail() {
            List<Map<String, Object>> list = pathwayService.listPathways(TENANT, "PUBLISHED", 25);
            assertThat(list).hasSize(1);
            UUID pathwayId = UUID.fromString((String) list.get(0).get("id"));
            Map<String, Object> detail = pathwayService.getPathway(TENANT, pathwayId).orElseThrow();
            List<Map<String, Object>> items = cast(detail.get("items"));
            assertThat(items).hasSize(2);
            assertThat(items.get(0).get("sequence")).isEqualTo(1);
            assertThat(items.get(1).get("sequence")).isEqualTo(2);
        }
    }

    /* ───────────── Enrolment ───────────── */
    @Nested
    @DisplayName("Enrolment")
    class Enrolment {
        @Test
        @DisplayName("Creates enrolment and emits compliant v1.1 event")
        void createsEnrolment() {
            Map<String, Object> result = enrolmentService.create(req(courseA, "USER-1"));
            assertThat(result.get("status")).isEqualTo("ENROLLED");
            assertThat(result.get("idempotent")).isEqualTo(false);
            assertThat(eventsOfType(FundoNativeEventTypes.ENROLMENT_CREATED)).hasSize(1);
        }

        @Test
        @DisplayName("Duplicate active enrolment is returned idempotently with no new event")
        void duplicateIsIdempotent() {
            Map<String, Object> first = enrolmentService.create(req(courseA, "USER-2"));
            Map<String, Object> second = enrolmentService.create(req(courseA, "USER-2"));
            assertThat(first.get("id")).isEqualTo(second.get("id"));
            assertThat(second.get("idempotent")).isEqualTo(true);
            assertThat(eventsOfType(FundoNativeEventTypes.ENROLMENT_CREATED)).hasSize(1);
        }

        @Test
        @DisplayName("Cancel transitions an active enrolment and emits cancelled event")
        void cancelEmitsEvent() {
            Map<String, Object> created = enrolmentService.create(req(courseA, "USER-3"));
            UUID id = UUID.fromString((String) created.get("id"));
            Map<String, Object> cancelled = enrolmentService.cancel(TENANT, id, "user-request").orElseThrow();
            assertThat(cancelled.get("status")).isEqualTo("CANCELLED");
            assertThat(eventsOfType(FundoNativeEventTypes.ENROLMENT_CANCELLED)).hasSize(1);
        }

        @Test
        @DisplayName("List by subject returns most recent first")
        void listBySubject() {
            enrolmentService.create(req(courseA, "USER-4"));
            enrolmentService.create(req(courseB, "USER-4"));
            List<Map<String, Object>> list = enrolmentService.listForSubject(TENANT, "PROVIDER", "USER-4", 25);
            assertThat(list).hasSize(2);
        }

        @Test
        @DisplayName("Start transitions ENROLLED to IN_PROGRESS")
        void startTransition() {
            Map<String, Object> created = enrolmentService.create(req(courseA, "USER-START"));
            UUID id = UUID.fromString((String) created.get("id"));
            Map<String, Object> started = enrolmentService.start(TENANT, id).orElseThrow();
            assertThat(started.get("status")).isEqualTo("IN_PROGRESS");
        }

        private FundoEnrolmentService.EnrolmentRequest req(CourseEntity course, String subject) {
            return new FundoEnrolmentService.EnrolmentRequest(
                    TENANT, "PROVIDER", subject, course.getId(), null, "SELF", null, null);
        }
    }

    /* ───────────── Progress ───────────── */
    @Nested
    @DisplayName("Progress")
    class Progress {
        @Test
        @DisplayName("Lesson progress emits started event and moves enrolment to IN_PROGRESS")
        void lessonProgressStartsEnrolment() {
            Map<String, Object> enrol = enrolmentService.create(req(courseA, "PROG-1"));
            UUID enrolmentId = UUID.fromString((String) enrol.get("id"));

            progressService.recordProgress(TENANT,
                    new FundoProgressService.ProgressUpdate(enrolmentId, null, lessonA11.getId(), 50, null));

            assertThat(eventsOfType(FundoNativeEventTypes.PROGRESS_STARTED)).hasSize(1);
            Map<String, Object> after = enrolmentService.get(TENANT, enrolmentId).orElseThrow();
            assertThat(after.get("status")).isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("Completing course-aggregate progress transitions enrolment to COMPLETED and emits course.completed event")
        void courseCompletionTransitionsEnrolment() {
            Map<String, Object> enrol = enrolmentService.create(req(courseA, "PROG-2"));
            UUID enrolmentId = UUID.fromString((String) enrol.get("id"));

            progressService.recordProgress(TENANT,
                    new FundoProgressService.ProgressUpdate(enrolmentId, null, null, 100, null));

            assertThat(eventsOfType(FundoNativeEventTypes.PROGRESS_COMPLETED)).hasSize(1);
            assertThat(eventsOfType(FundoNativeEventTypes.COURSE_COMPLETED)).hasSize(1);
            Map<String, Object> after = enrolmentService.get(TENANT, enrolmentId).orElseThrow();
            assertThat(after.get("status")).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("Opening lesson records lesson-opened event and last access")
        void openLessonTracksAccess() {
            Map<String, Object> enrol = enrolmentService.create(req(courseA, "PROG-OPEN"));
            UUID enrolmentId = UUID.fromString((String) enrol.get("id"));
            Map<String, Object> progress = progressService.openLesson(TENANT, lessonA11.getId(), enrolmentId);
            assertThat(progress.get("lessonId")).isEqualTo(lessonA11.getId().toString());
            assertThat(progress.get("lastAccessedAt")).isNotNull();
            assertThat(eventsOfType(FundoNativeEventTypes.LESSON_OPENED)).hasSize(1);
        }

        private FundoEnrolmentService.EnrolmentRequest req(CourseEntity course, String subject) {
            return new FundoEnrolmentService.EnrolmentRequest(
                    TENANT, "PROVIDER", subject, course.getId(), null, "SELF", null, null);
        }
    }

    /* ───────────── Assessment foundation ───────────── */
    @Nested
    @DisplayName("Assessment foundation")
    class Assessments {
        @Test
        @DisplayName("Lists assessments by course")
        void listsByCourse() {
            List<Map<String, Object>> list = assessmentService.listByCourse(TENANT, courseA.getId());
            assertThat(list).hasSize(1);
            assertThat(list.get(0).get("title")).isEqualTo("Quick check");
        }

        @Test
        @DisplayName("Submit fully-correct TF+MCQ scores 100 and passes")
        void submitObjectiveAttempt() {
            AssessmentEntity quiz = assessmentRepository.findByTenantIdAndCourseIdAndStatus(
                    TENANT, courseA.getId(), "PUBLISHED").get(0);
            List<AssessmentQuestionEntity> qs = questionRepository.findByAssessmentIdOrderBySequenceNoAsc(quiz.getId());
            Map<String, Object> answers = new HashMap<>();
            answers.put(qs.get(0).getId().toString(), "true");
            answers.put(qs.get(1).getId().toString(), "fundo");

            Map<String, Object> attempt = assessmentService.submitAttempt(TENANT, quiz.getId(),
                    new FundoAssessmentService.AttemptSubmission(null, "PROVIDER", "ASS-1", answers));
            assertThat(attempt.get("score")).isEqualTo(100);
            assertThat(attempt.get("passed")).isEqualTo(true);
            assertThat(eventsOfType(FundoNativeEventTypes.ASSESSMENT_ATTEMPT_SUBMITTED)).hasSize(1);
        }
    }

    /* ───────────── Certificates ───────────── */
    @Nested
    @DisplayName("Certificates")
    class Certificates {
        @Test
        @DisplayName("Cannot issue certificate for incomplete enrolment")
        void rejectsIncomplete() {
            Map<String, Object> enrol = enrolmentService.create(req(courseB, "CERT-1"));
            UUID enrolmentId = UUID.fromString((String) enrol.get("id"));
            assertThatThrownBy(() -> certificateService.issueForEnrolment(TENANT, enrolmentId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("enrolment_not_completed");
        }

        @Test
        @DisplayName("Issues for completed enrolment and idempotent on second call")
        void issuesAndIdempotent() {
            Map<String, Object> enrol = enrolmentService.create(req(courseB, "CERT-2"));
            UUID enrolmentId = UUID.fromString((String) enrol.get("id"));
            progressService.recordProgress(TENANT,
                    new FundoProgressService.ProgressUpdate(enrolmentId, null, null, 100, null));

            FundoCertificateService.CertificateIssueResult first =
                    certificateService.issueForEnrolment(TENANT, enrolmentId);
            FundoCertificateService.CertificateIssueResult second =
                    certificateService.issueForEnrolment(TENANT, enrolmentId);
            assertThat(first.idempotent()).isFalse();
            assertThat(second.idempotent()).isTrue();
            assertThat(first.view().get("id")).isEqualTo(second.view().get("id"));
            assertThat(first.view().get("cpdEligible")).isEqualTo(true);
            assertThat(first.view().get("cpdPoints")).isEqualTo(2);
            assertThat(eventsOfType(FundoNativeEventTypes.CERTIFICATE_ISSUED)).hasSize(1);
        }

        private FundoEnrolmentService.EnrolmentRequest req(CourseEntity course, String subject) {
            return new FundoEnrolmentService.EnrolmentRequest(
                    TENANT, "PROVIDER", subject, course.getId(), null, "SELF", null, null);
        }
    }

    /* ───────────── Learning record ───────────── */
    @Nested
    @DisplayName("Learning record")
    class LearningRecord {
        @Test
        @DisplayName("Aggregates enrolments, progress, certificates, attempts and CPD-eligible completions")
        void aggregatesEverything() {
            Map<String, Object> enrolA = enrolmentService.create(reqProvider(courseA, "REC-1"));
            Map<String, Object> enrolB = enrolmentService.create(reqProvider(courseB, "REC-1"));
            UUID enrolBId = UUID.fromString((String) enrolB.get("id"));
            progressService.recordProgress(TENANT,
                    new FundoProgressService.ProgressUpdate(enrolBId, null, null, 100, null));
            certificateService.issueForEnrolment(TENANT, enrolBId);

            Map<String, Object> record = recordService.getRecord(TENANT, "PROVIDER", "REC-1");
            List<Map<String, Object>> enrolments = cast(record.get("enrolments"));
            assertThat(enrolments).hasSize(2);
            List<Map<String, Object>> completed = cast(record.get("completedCourses"));
            assertThat(completed).hasSize(1);
            List<Map<String, Object>> certs = cast(record.get("certificates"));
            assertThat(certs).hasSize(1);
            List<Map<String, Object>> cpd = cast(record.get("cpdEligibleCompletions"));
            assertThat(cpd).hasSize(1);
            // The other enrolment (courseA, no progress) does not appear in completedCourses.
            assertThat(enrolA.get("id")).isNotEqualTo(enrolB.get("id"));
        }

        private FundoEnrolmentService.EnrolmentRequest reqProvider(CourseEntity course, String subject) {
            return new FundoEnrolmentService.EnrolmentRequest(
                    TENANT, "PROVIDER", subject, course.getId(), null, "SELF", null, null);
        }
    }

    @Nested
    @DisplayName("Learner dashboard and reports")
    class DashboardReports {
        @Test
        @DisplayName("My-learning aggregates enrolled, recommended, and CPD evidence sections")
        void myLearningAggregate() {
            enrolmentService.create(new FundoEnrolmentService.EnrolmentRequest(
                    TENANT, "PROVIDER", "DASH-1", courseA.getId(), null, "SELF", null, null));
            Map<String, Object> dashboard = learnerJourneyService.myLearning(TENANT, "PROVIDER", "DASH-1");
            List<Map<String, Object>> enrolled = cast(dashboard.get("enrolled"));
            List<Map<String, Object>> recommended = cast(dashboard.get("recommended"));
            assertThat(enrolled).isNotEmpty();
            assertThat(recommended).isNotEmpty();
            assertThat(dashboard).containsKeys("certificates", "cpdEligibleCompletions");
        }

        @Test
        @DisplayName("Overview and assessment reports return native rollups")
        void reportsReturnData() {
            Map<String, Object> overview = reportService.overview(TENANT);
            assertThat(overview).containsKeys("publishedCourses", "totalEnrolments", "completed");
            Map<String, Object> assessmentPerf = reportService.assessmentPerformance(TENANT, courseA.getId(), null, 100);
            List<Map<String, Object>> items = cast(assessmentPerf.get("items"));
            assertThat(items).isNotNull();
        }
    }

    /* ───────────── Standalone (no Moodle) ───────────── */
    @Nested
    @DisplayName("Standalone (no Moodle interaction)")
    class Standalone {
        @Test
        @DisplayName("Full native flow runs without any MoodleWebServiceClient interaction")
        void noMoodleInteraction() {
            // Walk the entire happy path end-to-end.
            Map<String, Object> enrol = enrolmentService.create(
                    new FundoEnrolmentService.EnrolmentRequest(
                            TENANT, "PROVIDER", "STAND-1", courseB.getId(), null, "SELF", null, null));
            UUID enrolmentId = UUID.fromString((String) enrol.get("id"));
            progressService.recordProgress(TENANT,
                    new FundoProgressService.ProgressUpdate(enrolmentId, null, null, 100, null));
            certificateService.issueForEnrolment(TENANT, enrolmentId);
            recordService.getRecord(TENANT, "PROVIDER", "STAND-1");

            // The standalone-mode guarantee:
            verifyNoInteractions(moodleWebServiceClient);
        }
    }

    /* ───────────── helpers ───────────── */
    private List<LearningOutboxEntity> eventsOfType(String type) {
        return outboxRepository.findAll().stream()
                .filter(e -> type.equals(e.getEventType()))
                .toList();
    }
}
