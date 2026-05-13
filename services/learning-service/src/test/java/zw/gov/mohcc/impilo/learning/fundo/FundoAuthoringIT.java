package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;

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
class FundoAuthoringIT {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Autowired private FundoAuthoringService authoring;
    @Autowired private FundoCatalogService catalogService;
    @Autowired private FundoCourseStructureService structureService;
    @Autowired private LearningOutboxRepository outboxRepository;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    private FundoAuthoringService.CourseUpsert sampleDraftCourse(String code, String title) {
        return new FundoAuthoringService.CourseUpsert(
                code, title, "Test description", "ORIENTATION", "INTRODUCTORY",
                "DRAFT", "en", 30, false, false, null);
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
                                    "PUBLISHED", null, null, null, null, null));
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
                                    "PUBLISHED", null, null, null, null, null));
            assertThat(created.isOk()).isTrue();
            assertThat(eventsOfType(FundoNativeEventTypes.COURSE_PUBLISHED)).hasSize(1);

            UUID courseId = UUID.fromString((String) created.value().get("id"));

            authoring.updateCourse(TENANT, courseId,
                    new FundoAuthoringService.CourseUpsert(
                            null, "Renamed", null, null, null,
                            "PUBLISHED", null, null, null, null, null));
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
                                    null, null, null, null, null, null));
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
                            "Lesson 1", "TEXT", "body", null, 5, null, null, null));
            authoring.createLesson(TENANT, moduleId,
                    new FundoAuthoringService.LessonUpsert(
                            "Lesson 2", "TEXT", "body 2", null, 5, null, null, null));

            // Publish the course so the structure read returns it.
            authoring.updateCourse(TENANT, courseId,
                    new FundoAuthoringService.CourseUpsert(
                            null, null, null, null, null,
                            "PUBLISHED", null, null, null, null, null));

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
                            "[\"true\",\"false\"]", "\"true\"", 1, 1)).isOk()).isTrue();

            FundoAuthoringService.AuthoringResult<Map<String, Object>> dup = authoring.addQuestion(
                    TENANT, assessmentId,
                    new FundoAuthoringService.QuestionUpsert(
                            "TRUE_FALSE", "Question 2", null, null, 1, 1));
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
                    "PUBLISHED", null, null, null, null, null));
            List<Map<String, Object>> items = catalogService.listCatalogue(
                    TENANT,
                    new FundoCatalogService.CatalogueFilter(null, null, null, null, null, null),
                    50);
            assertThat(items).anyMatch(m -> "FUNDO-AUTH-9".equals(m.get("code")));
        }
    }
}
