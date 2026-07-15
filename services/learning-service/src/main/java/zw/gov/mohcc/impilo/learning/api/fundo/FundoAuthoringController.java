package zw.gov.mohcc.impilo.learning.api.fundo;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.learning.fundo.FundoAuthoringService;
import zw.gov.mohcc.impilo.learning.fundo.FundoAuthoringService.AuthoringResult;

/**
 * Native Fundo authoring surface for catalogue, course structure, pathways,
 * assessments and questions. The controller is write-only; read models stay
 * with the focused read controllers.
 */
@RestController
@RequestMapping("/internal/v1/learning/fundo")
public class FundoAuthoringController {

    private final FundoAuthoringService authoring;

    public FundoAuthoringController(FundoAuthoringService authoring) {
        this.authoring = authoring;
    }

    // ── Course ────────────────────────────────────────────────

    @PostMapping("/catalog")
    public ResponseEntity<Map<String, Object>> createCourse(
            @RequestBody(required = false) FundoAuthoringService.CourseUpsert body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireSystemAdmin();
        if (forbidden != null) return forbidden;
        UUID tenantId = currentTenant();
        if (tenantId == null) return tenantInvalid();
        return respond("course", authoring.createCourse(tenantId, body));
    }

    @PutMapping("/catalog/{courseId}")
    public ResponseEntity<Map<String, Object>> updateCourse(
            @PathVariable String courseId,
            @RequestBody(required = false) FundoAuthoringService.CourseUpsert body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireSystemAdmin();
        if (forbidden != null) return forbidden;
        UUID tenantId = currentTenant();
        UUID cid = FundoApiSupport.tryParseUuid(courseId);
        if (tenantId == null || cid == null) {
            return FundoApiSupport.notFound("COURSE_NOT_FOUND", "Course not found");
        }
        return respond("course", authoring.updateCourse(tenantId, cid,
                body == null ? new FundoAuthoringService.CourseUpsert(
                        null, null, null, null, null, null, null, null, null, null, null) : body));
    }

    // ── Module ────────────────────────────────────────────────

    @PostMapping("/courses/{courseId}/modules")
    public ResponseEntity<Map<String, Object>> createModule(
            @PathVariable String courseId,
            @RequestBody(required = false) FundoAuthoringService.ModuleUpsert body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireSystemAdmin();
        if (forbidden != null) return forbidden;
        UUID tenantId = currentTenant();
        UUID cid = FundoApiSupport.tryParseUuid(courseId);
        if (tenantId == null || cid == null) {
            return FundoApiSupport.notFound("COURSE_NOT_FOUND", "Course not found");
        }
        return respond("module", authoring.createModule(tenantId, cid, body));
    }

    @PutMapping("/modules/{moduleId}")
    public ResponseEntity<Map<String, Object>> updateModule(
            @PathVariable String moduleId,
            @RequestBody(required = false) FundoAuthoringService.ModuleUpsert body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireSystemAdmin();
        if (forbidden != null) return forbidden;
        UUID tenantId = currentTenant();
        UUID mid = FundoApiSupport.tryParseUuid(moduleId);
        if (tenantId == null || mid == null) {
            return FundoApiSupport.notFound("MODULE_NOT_FOUND", "Module not found");
        }
        return respond("module", authoring.updateModule(tenantId, mid,
                body == null ? new FundoAuthoringService.ModuleUpsert(null, null, null, null) : body));
    }

    // ── Lesson ────────────────────────────────────────────────

    @PostMapping("/modules/{moduleId}/lessons")
    public ResponseEntity<Map<String, Object>> createLesson(
            @PathVariable String moduleId,
            @RequestBody(required = false) FundoAuthoringService.LessonUpsert body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireSystemAdmin();
        if (forbidden != null) return forbidden;
        UUID tenantId = currentTenant();
        UUID mid = FundoApiSupport.tryParseUuid(moduleId);
        if (tenantId == null || mid == null) {
            return FundoApiSupport.notFound("MODULE_NOT_FOUND", "Module not found");
        }
        return respond("lesson", authoring.createLesson(tenantId, mid, body));
    }

    @PutMapping("/lessons/{lessonId}")
    public ResponseEntity<Map<String, Object>> updateLesson(
            @PathVariable String lessonId,
            @RequestBody(required = false) FundoAuthoringService.LessonUpsert body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireSystemAdmin();
        if (forbidden != null) return forbidden;
        UUID tenantId = currentTenant();
        UUID lid = FundoApiSupport.tryParseUuid(lessonId);
        if (tenantId == null || lid == null) {
            return FundoApiSupport.notFound("LESSON_NOT_FOUND", "Lesson not found");
        }
        return respond("lesson", authoring.updateLesson(tenantId, lid,
                body == null ? new FundoAuthoringService.LessonUpsert(
                        null, null, null, null, null, null, null, null, null, null) : body));
    }

    // ── Pathway ───────────────────────────────────────────────

    @PostMapping("/pathways")
    public ResponseEntity<Map<String, Object>> createPathway(
            @RequestBody(required = false) FundoAuthoringService.PathwayUpsert body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireSystemAdmin();
        if (forbidden != null) return forbidden;
        UUID tenantId = currentTenant();
        if (tenantId == null) return tenantInvalid();
        return respond("pathway", authoring.createPathway(tenantId, body));
    }

    @PutMapping("/pathways/{pathwayId}")
    public ResponseEntity<Map<String, Object>> updatePathway(
            @PathVariable String pathwayId,
            @RequestBody(required = false) FundoAuthoringService.PathwayUpsert body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireSystemAdmin();
        if (forbidden != null) return forbidden;
        UUID tenantId = currentTenant();
        UUID pid = FundoApiSupport.tryParseUuid(pathwayId);
        if (tenantId == null || pid == null) {
            return FundoApiSupport.notFound("PATHWAY_NOT_FOUND", "Pathway not found");
        }
        return respond("pathway", authoring.updatePathway(tenantId, pid,
                body == null ? new FundoAuthoringService.PathwayUpsert(
                        null, null, null, null, null, null, null) : body));
    }

    @PostMapping("/pathways/{pathwayId}/items")
    public ResponseEntity<Map<String, Object>> addPathwayItem(
            @PathVariable String pathwayId,
            @RequestBody(required = false) FundoAuthoringService.PathwayItemUpsert body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireSystemAdmin();
        if (forbidden != null) return forbidden;
        UUID tenantId = currentTenant();
        UUID pid = FundoApiSupport.tryParseUuid(pathwayId);
        if (tenantId == null || pid == null) {
            return FundoApiSupport.notFound("PATHWAY_NOT_FOUND", "Pathway not found");
        }
        return respond("item", authoring.addPathwayItem(tenantId, pid, body));
    }

    // ── Assessment + Question ────────────────────────────────

    @PostMapping("/assessments")
    public ResponseEntity<Map<String, Object>> createAssessment(
            @RequestBody(required = false) FundoAuthoringService.AssessmentUpsert body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireSystemAdmin();
        if (forbidden != null) return forbidden;
        UUID tenantId = currentTenant();
        if (tenantId == null) return tenantInvalid();
        return respond("assessment", authoring.createAssessment(tenantId, body));
    }

    @PutMapping("/assessments/{assessmentId}")
    public ResponseEntity<Map<String, Object>> updateAssessment(
            @PathVariable String assessmentId,
            @RequestBody(required = false) FundoAuthoringService.AssessmentUpsert body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireSystemAdmin();
        if (forbidden != null) return forbidden;
        UUID tenantId = currentTenant();
        UUID aid = FundoApiSupport.tryParseUuid(assessmentId);
        if (tenantId == null || aid == null) {
            return FundoApiSupport.notFound("ASSESSMENT_NOT_FOUND", "Assessment not found");
        }
        return respond("assessment", authoring.updateAssessment(tenantId, aid,
                body == null ? new FundoAuthoringService.AssessmentUpsert(
                        null, null, null, null, null, null, null, null) : body));
    }

    @PostMapping("/assessments/{assessmentId}/questions")
    public ResponseEntity<Map<String, Object>> addQuestion(
            @PathVariable String assessmentId,
            @RequestBody(required = false) FundoAuthoringService.QuestionUpsert body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireSystemAdmin();
        if (forbidden != null) return forbidden;
        UUID tenantId = currentTenant();
        UUID aid = FundoApiSupport.tryParseUuid(assessmentId);
        if (tenantId == null || aid == null) {
            return FundoApiSupport.notFound("ASSESSMENT_NOT_FOUND", "Assessment not found");
        }
        return respond("question", authoring.addQuestion(tenantId, aid, body));
    }

    @PutMapping("/assessments/{assessmentId}/questions/{questionId}")
    public ResponseEntity<Map<String, Object>> updateQuestion(
            @PathVariable String assessmentId,
            @PathVariable String questionId,
            @RequestBody(required = false) FundoAuthoringService.QuestionUpsert body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireSystemAdmin();
        if (forbidden != null) return forbidden;
        UUID tenantId = currentTenant();
        UUID aid = FundoApiSupport.tryParseUuid(assessmentId);
        UUID qid = FundoApiSupport.tryParseUuid(questionId);
        if (tenantId == null || aid == null || qid == null) {
            return FundoApiSupport.notFound("QUESTION_NOT_FOUND", "Question not found");
        }
        return respond("question", authoring.updateQuestion(tenantId, aid, qid, body));
    }

    // ── helpers ──────────────────────────────────────────────

    private static UUID currentTenant() {
        return FundoApiSupport.currentTenant();
    }

    private static ResponseEntity<Map<String, Object>> tenantInvalid() {
        return FundoApiSupport.invalidTenant();
    }

    private static ResponseEntity<Map<String, Object>> respond(
            String key, AuthoringResult<Map<String, Object>> result) {
        return switch (result.kind()) {
            case OK -> FundoApiSupport.dataEnvelope(key, result.value());
            case BAD_REQUEST -> FundoApiSupport.badRequest(result.code(), result.message());
            case NOT_FOUND -> FundoApiSupport.notFound(result.code(), result.message());
            case CONFLICT -> FundoApiSupport.conflict(result.code(), result.message());
        };
    }
}
