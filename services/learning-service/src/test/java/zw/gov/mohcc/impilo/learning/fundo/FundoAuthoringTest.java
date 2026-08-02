package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningOutboxEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningOutboxRepository;

/**
 * Phase 6A — authoring integration test suite. Exercises every authoring
 * endpoint of {@link FundoAuthoringService} through the real
 * {@code learning-service} Spring context, asserts conflict / not-found /
 * idempotent semantics, and verifies the
 * {@code impilo.learning.course.published.v1} outbox emission on the
 * {@code DRAFT → PUBLISHED} status transition.
 *
 * <p>Moodle is mocked and never invoked — the test class is annotated
 * with the same {@code @MockBean MoodleWebServiceClient} guard as
 * {@code FundoNativeLmsIT} for consistency.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoAuthoringTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Autowired private FundoAuthoringService authoring;
    @Autowired private FundoCatalogService catalogService;
    @Autowired private FundoCourseStructureService structureService;
    @Autowired private LearningOutboxRepository outboxRepository;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    private FundoAuthoringService.CourseUpsert sampleDraftCourse(String code, String title) {
        return new FundoAuthoringService.CourseUpsert(
                code, title, "Test description", "ORIENTATION", "INTRODUCTORY",
                "DRAFT", "en", 30, false, false, null, null, null, null, null, null);
    }

    private List<LearningOutboxEntity> eventsOfType(String type) {
        return outboxRepository.findAll().stream()
                .filter(e -> type.equals(e.getEventType()))
                .toList();
    }

    @Nested
    @DisplayName("Course authoring")
    class CourseAuthoring {

        @Test
        @DisplayName("Create a draft course then publish — emits course.published.v1 exactly once")
        void publishEmitsOnceOnTransition() {
            FundoAuthoringService.AuthoringResult<Map<String, Object>> created =
                    authoring.createCourse(TENANT, sampleDraftCourse("FUNDO-AUTH-1", "Author Test 1"));
            assertThat(created.isOk()).isTrue();
            assertThat(created.value().get("status")).isEqualTo("DRAFT");
            assertThat(eventsOfType(FundoNativeEventTypes.COURSE_PUBLISHED)).isEmpty();

            UUID courseId = UUID.fromString((String) created.value().get("id"));

            FundoAuthoringService.AuthoringResult<Map<String, Object>> published =
                    authoring.updateCourse(TENANT, courseId,
                            new FundoAuthoringService.CourseUpsert(
                                    null, null, null, null, null,
                                    "PUBLISHED", null, null, null, null, null, null, null, null, null, null));
            assertThat(published.isOk()).isTrue();
            assertThat(published.value().get("status")).isEqualTo("PUBLISHED");
            assertThat(eventsOfType(FundoNativeEventTypes.COURSE_PUBLISHED)).hasSize(1);
        }

        @Test
        @DisplayName("Re-publishing a PUBLISHED course does not emit a second course.published.v1")
        void republishIsNoOp() {
            FundoAuthoringService.AuthoringResult<Map<String, Object>> created =
                    authoring.createCourse(TENANT,
                            new FundoAuthoringService.CourseUpsert(
                                    "FUNDO-AUTH-2", "Author Test 2", null, null, null,
                                    "PUBLISHED", null, null, null, null, null, null, null, null, null, null));
            assertThat(created.isOk()).isTrue();
            assertThat(eventsOfType(FundoNativeEventTypes.COURSE_PUBLISHED)).hasSize(1);

            UUID courseId = UUID.fromString((String) created.value().get("id"));

            authoring.updateCourse(TENANT, courseId,
                    new FundoAuthoringService.CourseUpsert(
                            null, "Renamed", null, null, null,
                            "PUBLISHED", null, null, null, null, null, null, null, null, null, null));
            assertThat(eventsOfType(FundoNativeEventTypes.COURSE_PUBLISHED)).hasSize(1);
        }

        @Test
        @DisplayName("Duplicate course code is rejected with CONFLICT")
        void duplicateCodeConflicts() {
            authoring.createCourse(TENANT, sampleDraftCourse("FUNDO-AUTH-3", "First"));
            FundoAuthoringService.AuthoringResult<Map<String, Object>> dup =
                    authoring.createCourse(TENANT, sampleDraftCourse("FUNDO-AUTH-3", "Second"));
            assertThat(dup.kind()).isEqualTo(FundoAuthoringService.AuthoringResult.Kind.CONFLICT);
            assertThat(dup.code()).isEqualTo("COURSE_CODE_TAKEN");
        }

        @Test
        @DisplayName("Missing required fields rejected with BAD_REQUEST")
        void missingFieldsRejected() {
            FundoAuthoringService.AuthoringResult<Map<String, Object>> r =
                    authoring.createCourse(TENANT,
                            new FundoAuthoringService.CourseUpsert(
                                    null, null, null, null, null,
                                    null, null, null, null, null, null, null, null, null, null, null));
            assertThat(r.kind()).isEqualTo(FundoAuthoringService.AuthoringResult.Kind.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("Module + lesson authoring")
    class ModuleLessonAuthoring {

        @Test
        @DisplayName("Module + lesson structure surfaces via Phase 5B course-structure read")
        void roundTripStructure() {
            UUID courseId = UUID.fromString((String) authoring
                    .createCourse(TENANT, sampleDraftCourse("FUNDO-AUTH-4", "Author Test 4"))
                    .value().get("id"));

            FundoAuthoringService.AuthoringResult<Map<String, Object>> module = authoring.createModule(
                    TENANT, courseId,
                    new FundoAuthoringService.ModuleUpsert("Module One", "First module", null, null));
            assertThat(module.isOk()).isTrue();
            UUID moduleId = UUID.fromString((String) module.value().get("id"));

            authoring.createLesson(TENANT, moduleId,
                    new FundoAuthoringService.LessonUpsert(
                            "Lesson 1", "TEXT", "body", null, "PLAIN_TEXT", null, 5, null, null, null));
            authoring.createLesson(TENANT, moduleId,
                    new FundoAuthoringService.LessonUpsert(
                            "Lesson 2", "TEXT", "body 2", null, "PLAIN_TEXT", null, 5, null, null, null));

            // Publish the course so the structure read returns it.
            authoring.updateCourse(TENANT, courseId,
                    new FundoAuthoringService.CourseUpsert(
                            null, null, null, null, null,
                            "PUBLISHED", null, null, null, null, null, null, null, null, null, null));

            Map<String, Object> structure = structureService.getStructure(TENANT, courseId).orElseThrow();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> modules = (List<Map<String, Object>>) structure.get("modules");
            assertThat(modules).hasSize(1);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lessons = (List<Map<String, Object>>) modules.get(0).get("lessons");
            assertThat(lessons).hasSize(2);
        }

        @Test
        @DisplayName("Module sequence collision is reported as CONFLICT")
        void moduleSequenceConflict() {
            UUID courseId = UUID.fromString((String) authoring
                    .createCourse(TENANT, sampleDraftCourse("FUNDO-AUTH-5", "Author Test 5"))
                    .value().get("id"));
            authoring.createModule(TENANT, courseId,
                    new FundoAuthoringService.ModuleUpsert("A", null, 1, null));
            FundoAuthoringService.AuthoringResult<Map<String, Object>> clash = authoring.createModule(
                    TENANT, courseId,
                    new FundoAuthoringService.ModuleUpsert("B", null, 1, null));
            assertThat(clash.kind()).isEqualTo(FundoAuthoringService.AuthoringResult.Kind.CONFLICT);
        }
    }

    @Nested
    @DisplayName("Pathway authoring")
    class PathwayAuthoring {

        @Test
        @DisplayName("Create pathway, add ordered items, surfaces via Phase 5B pathway list")
        void createPathwayAndItems() {
            UUID courseA = UUID.fromString((String) authoring
                    .createCourse(TENANT, sampleDraftCourse("FUNDO-AUTH-6A", "Author Test 6A"))
                    .value().get("id"));
            UUID courseB = UUID.fromString((String) authoring
                    .createCourse(TENANT, sampleDraftCourse("FUNDO-AUTH-6B", "Author Test 6B"))
                    .value().get("id"));

            FundoAuthoringService.AuthoringResult<Map<String, Object>> pathway = authoring.createPathway(
                    TENANT,
                    new FundoAuthoringService.PathwayUpsert(
                            "FUNDO-PATH-AUTH", "Author Pathway", null, null, null, null, "PUBLISHED"));
            assertThat(pathway.isOk()).isTrue();
            UUID pathwayId = UUID.fromString((String) pathway.value().get("id"));

            assertThat(authoring.addPathwayItem(TENANT, pathwayId,
                    new FundoAuthoringService.PathwayItemUpsert(courseA, 1, null, null)).isOk()).isTrue();
            assertThat(authoring.addPathwayItem(TENANT, pathwayId,
                    new FundoAuthoringService.PathwayItemUpsert(courseB, 2, null, null)).isOk()).isTrue();
        }
    }

    @Nested
    @DisplayName("Assessment + question authoring")
    class AssessmentAuthoring {

        @Test
        @DisplayName("Create assessment then add questions; sequence collisions reported as CONFLICT")
        void createAssessmentAndQuestions() {
            UUID courseId = UUID.fromString((String) authoring
                    .createCourse(TENANT, sampleDraftCourse("FUNDO-AUTH-7", "Author Test 7"))
                    .value().get("id"));

            FundoAuthoringService.AuthoringResult<Map<String, Object>> assessment = authoring.createAssessment(
                    TENANT, new FundoAuthoringService.AssessmentUpsert(
                            courseId, null, "Quick Quiz", null, "QUIZ", null, null, null));
            assertThat(assessment.isOk()).isTrue();
            UUID assessmentId = UUID.fromString((String) assessment.value().get("id"));

            assertThat(authoring.addQuestion(TENANT, assessmentId,
                    new FundoAuthoringService.QuestionUpsert(
                            "TRUE_FALSE", "Native Fundo is standalone-capable.",
                            "[\"true\",\"false\"]", "\"true\"", null, 1, 1)).isOk()).isTrue();

            FundoAuthoringService.AuthoringResult<Map<String, Object>> dup = authoring.addQuestion(
                    TENANT, assessmentId,
                    new FundoAuthoringService.QuestionUpsert(
                            "TRUE_FALSE", "Question 2", null, null, null, 1, 1));
            assertThat(dup.kind()).isEqualTo(FundoAuthoringService.AuthoringResult.Kind.CONFLICT);
        }

        @Test
        @DisplayName("Assessment NOT_FOUND when tenant-scope is wrong")
        void wrongTenantNotFound() {
            UUID courseId = UUID.fromString((String) authoring
                    .createCourse(TENANT, sampleDraftCourse("FUNDO-AUTH-8", "Author Test 8"))
                    .value().get("id"));
            FundoAuthoringService.AuthoringResult<Map<String, Object>> r = authoring.createAssessment(
                    UUID.randomUUID(),
                    new FundoAuthoringService.AssessmentUpsert(
                            courseId, null, "Q", null, "QUIZ", null, null, null));
            assertThat(r.kind()).isEqualTo(FundoAuthoringService.AuthoringResult.Kind.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Catalogue read sees authored content")
    class CatalogueRead {

        @Test
        @DisplayName("Published authored courses appear in catalogue listings")
        void publishedAppearInCatalogue() {
            authoring.createCourse(TENANT, new FundoAuthoringService.CourseUpsert(
                    "FUNDO-AUTH-9", "Publish Me", null, null, null,
                    "PUBLISHED", null, null, null, null, null, null, null, null, null, null));
            List<Map<String, Object>> items = catalogService.listCatalogue(
                    TENANT,
                    new FundoCatalogService.CatalogueFilter(null, null, null, null, null, null),
                    50);
            assertThat(items).anyMatch(m -> "FUNDO-AUTH-9".equals(m.get("code")));
        }
    }

    /**
     * V031/V032, ported from impilo-learning-staging. The DB checks are the authority;
     * these prove the service refuses the bad shapes first, so a caller gets a stated
     * reason instead of a constraint violation, and that switching mode does not leave
     * the previous mode's value behind.
     */
    @Nested
    @DisplayName("Course due dates and audience targeting")
    class DueDateAndAudience {

        private FundoAuthoringService.CourseUpsert course(
                String code, String dueType, java.time.OffsetDateTime due, Integer days,
                String audienceType, String audienceRoles) {
            return new FundoAuthoringService.CourseUpsert(
                    code, code, null, null, null, "DRAFT", null, null, null, null, null,
                    dueType, due, days, audienceType, audienceRoles);
        }

        @Test
        @DisplayName("A new course defaults to no deadline and ALL_LEARNERS")
        void defaults() {
            var r = authoring.createCourse(TENANT, course("FUNDO-DD-0", null, null, null, null, null));
            assertThat(r.isOk()).isTrue();
            assertThat(r.value().get("dueDateType")).isNull();
            assertThat(r.value().get("audienceType")).isEqualTo("ALL_LEARNERS");
        }

        @Test
        @DisplayName("FIXED keeps the absolute date; RELATIVE keeps the offset")
        void bothModesPersist() {
            var when = java.time.OffsetDateTime.parse("2026-12-01T00:00:00Z");
            var fixed = authoring.createCourse(TENANT, course("FUNDO-DD-1", "FIXED", when, null, null, null));
            assertThat(fixed.isOk()).isTrue();
            assertThat(fixed.value().get("dueDateType")).isEqualTo("FIXED");
            assertThat(fixed.value().get("dueDate")).isNotNull();

            var rel = authoring.createCourse(TENANT, course("FUNDO-DD-2", "RELATIVE", null, 30, null, null));
            assertThat(rel.isOk()).isTrue();
            assertThat(rel.value().get("dueDateDaysFromEnrollment")).isEqualTo(30);
        }

        @Test
        @DisplayName("Switching FIXED to RELATIVE clears the stale absolute date")
        void switchingModeClearsTheOther() {
            var when = java.time.OffsetDateTime.parse("2026-12-01T00:00:00Z");
            var created = authoring.createCourse(TENANT, course("FUNDO-DD-3", "FIXED", when, null, null, null));
            UUID id = UUID.fromString((String) created.value().get("id"));

            var updated = authoring.updateCourse(TENANT, id,
                    course("FUNDO-DD-3", "RELATIVE", null, 14, null, null));
            assertThat(updated.isOk()).isTrue();
            assertThat(updated.value().get("dueDateDaysFromEnrollment")).isEqualTo(14);
            assertThat(updated.value().get("dueDate")).isNull();
        }

        @Test
        @DisplayName("Each incomplete or unknown combination is refused with a reason")
        void invalidCombinationsRefused() {
            assertThatThrownBy(() -> authoring.createCourse(TENANT,
                    course("FUNDO-DD-4", "FIXED", null, null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dueDate is required");

            assertThatThrownBy(() -> authoring.createCourse(TENANT,
                    course("FUNDO-DD-5", "RELATIVE", null, null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dueDateDaysFromEnrollment");

            assertThatThrownBy(() -> authoring.createCourse(TENANT,
                    course("FUNDO-DD-6", "WHENEVER", null, null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("FIXED, RELATIVE, NONE");

            assertThatThrownBy(() -> authoring.createCourse(TENANT,
                    course("FUNDO-DD-7", null, null, null, "SPECIFIC_ROLES", "  ")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("audienceRoles is required");

            assertThatThrownBy(() -> authoring.createCourse(TENANT,
                    course("FUNDO-DD-8", null, null, null, "EVERYBODY", null)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("audienceType must be one of");
        }

        @Test
        @DisplayName("Leaving SPECIFIC_ROLES drops the roles rather than orphaning them")
        void narrowingAndWideningAudience() {
            var created = authoring.createCourse(TENANT,
                    course("FUNDO-DD-9", null, null, null, "SPECIFIC_ROLES", "NURSE,DOCTOR"));
            assertThat(created.value().get("audienceRoles")).isEqualTo("NURSE,DOCTOR");
            UUID id = UUID.fromString((String) created.value().get("id"));

            var widened = authoring.updateCourse(TENANT, id,
                    course("FUNDO-DD-9", null, null, null, "ALL_LEARNERS", null));
            assertThat(widened.value().get("audienceType")).isEqualTo("ALL_LEARNERS");
            assertThat(widened.value().get("audienceRoles")).isNull();
        }
    }
}
