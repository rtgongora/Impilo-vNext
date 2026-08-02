package zw.gov.mohcc.impilo.learning.api.v11.fundo;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoAuthoringService;
import zw.gov.mohcc.impilo.learning.fundo.FundoAuthoringService.AuthoringResult;

/**
 * Phase 6A — native Fundo authoring v1.1 surface. Exposes write endpoints
 * for catalogue, course structure (modules + lessons), pathways (and items),
 * assessments and questions. Read endpoints continue to live on the Phase
 * 5B controllers ({@link FundoCatalogController}, {@link FundoPathwayController},
 * {@link FundoAssessmentController}); this controller is write-only.
 *
 * <p>All endpoints honour the v1.1 envelope ({@code {"data": ...}} on
 * success, {@code {"error": {code, message}}} on failure) and rely on the
 * platform v1.1 filters for tenant header enforcement and idempotency.</p>
 *
 * <p>The {@code DRAFT → PUBLISHED} status transition on a course emits a
 * single {@link zw.gov.mohcc.impilo.learning.fundo.FundoNativeEventTypes#COURSE_PUBLISHED}
 * outbox row inside the same transaction.</p>
 */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoAuthoringController {

    private final FundoAuthoringService authoring;

    public FundoAuthoringController(FundoAuthoringService authoring) {
        this.authoring = authoring;
    }

    // ── Course ────────────────────────────────────────────────

    @PostMapping("/catalog")
    public ResponseEntity<Map<String, Object>> createCourse(
            @RequestBody(required = false) FundoAuthoringService.CourseUpsert body) {
        UUID tenantId = currentTenant();
        if (tenantId == null) return tenantInvalid();
        return respond("course", authoring.createCourse(tenantId, body));
    }

    @PutMapping("/catalog/{courseId}")
    public ResponseEntity<Map<String, Object>> updateCourse(
            @PathVariable String courseId,
            @RequestBody(required = false) FundoAuthoringService.CourseUpsert body) {
        UUID tenantId = currentTenant();
        UUID cid = FundoV11Support.tryParseUuid(courseId);
        if (tenantId == null || cid == null) {
            return FundoV11Support.notFound("COURSE_NOT_FOUND", "Course not found");
        }
        return respond("course", authoring.updateCourse(tenantId, cid,
                body == null ? FundoAuthoringService.CourseUpsert.empty() : body));
    }

    // ── Module ────────────────────────────────────────────────

    @PostMapping("/courses/{courseId}/modules")
    public ResponseEntity<Map<String, Object>> createModule(
            @PathVariable String courseId,
            @RequestBody(required = false) FundoAuthoringService.ModuleUpsert body) {
        UUID tenantId = currentTenant();
        UUID cid = FundoV11Support.tryParseUuid(courseId);
        if (tenantId == null || cid == null) {
            return FundoV11Support.notFound("COURSE_NOT_FOUND", "Course not found");
        }
        return respond("module", authoring.createModule(tenantId, cid, body));
    }

    @PutMapping("/modules/{moduleId}")
    public ResponseEntity<Map<String, Object>> updateModule(
            @PathVariable String moduleId,
            @RequestBody(required = false) FundoAuthoringService.ModuleUpsert body) {
        UUID tenantId = currentTenant();
        UUID mid = FundoV11Support.tryParseUuid(moduleId);
        if (tenantId == null || mid == null) {
            return FundoV11Support.notFound("MODULE_NOT_FOUND", "Module not found");
        }
        return respond("module", authoring.updateModule(tenantId, mid,
                body == null ? new FundoAuthoringService.ModuleUpsert(null, null, null, null) : body));
    }

    // ── Lesson ────────────────────────────────────────────────

    @PostMapping("/modules/{moduleId}/lessons")
    public ResponseEntity<Map<String, Object>> createLesson(
            @PathVariable String moduleId,
            @RequestBody(required = false) FundoAuthoringService.LessonUpsert body) {
        UUID tenantId = currentTenant();
        UUID mid = FundoV11Support.tryParseUuid(moduleId);
        if (tenantId == null || mid == null) {
            return FundoV11Support.notFound("MODULE_NOT_FOUND", "Module not found");
        }
        return respond("lesson", authoring.createLesson(tenantId, mid, body));
    }

    @PutMapping("/lessons/{lessonId}")
    public ResponseEntity<Map<String, Object>> updateLesson(
            @PathVariable String lessonId,
            @RequestBody(required = false) FundoAuthoringService.LessonUpsert body) {
        UUID tenantId = currentTenant();
        UUID lid = FundoV11Support.tryParseUuid(lessonId);
        if (tenantId == null || lid == null) {
            return FundoV11Support.notFound("LESSON_NOT_FOUND", "Lesson not found");
        }
        return respond("lesson", authoring.updateLesson(tenantId, lid,
                body == null ? new FundoAuthoringService.LessonUpsert(
                        null, null, null, null, null, null, null, null, null, null) : body));
    }

    // ── Pathway ───────────────────────────────────────────────

    @PostMapping("/pathways")
    public ResponseEntity<Map<String, Object>> createPathway(
            @RequestBody(required = false) FundoAuthoringService.PathwayUpsert body) {
        UUID tenantId = currentTenant();
        if (tenantId == null) return tenantInvalid();
        return respond("pathway", authoring.createPathway(tenantId, body));
    }

    @PutMapping("/pathways/{pathwayId}")
    public ResponseEntity<Map<String, Object>> updatePathway(
            @PathVariable String pathwayId,
            @RequestBody(required = false) FundoAuthoringService.PathwayUpsert body) {
        UUID tenantId = currentTenant();
        UUID pid = FundoV11Support.tryParseUuid(pathwayId);
        if (tenantId == null || pid == null) {
            return FundoV11Support.notFound("PATHWAY_NOT_FOUND", "Pathway not found");
        }
        return respond("pathway", authoring.updatePathway(tenantId, pid,
                body == null ? new FundoAuthoringService.PathwayUpsert(
                        null, null, null, null, null, null, null) : body));
    }

    @PostMapping("/pathways/{pathwayId}/items")
    public ResponseEntity<Map<String, Object>> addPathwayItem(
            @PathVariable String pathwayId,
            @RequestBody(required = false) FundoAuthoringService.PathwayItemUpsert body) {
        UUID tenantId = currentTenant();
        UUID pid = FundoV11Support.tryParseUuid(pathwayId);
        if (tenantId == null || pid == null) {
            return FundoV11Support.notFound("PATHWAY_NOT_FOUND", "Pathway not found");
        }
        return respond("item", authoring.addPathwayItem(tenantId, pid, body));
    }

    // ── Assessment + Question ────────────────────────────────

    @PostMapping("/assessments")
    public ResponseEntity<Map<String, Object>> createAssessment(
            @RequestBody(required = false) FundoAuthoringService.AssessmentUpsert body) {
        UUID tenantId = currentTenant();
        if (tenantId == null) return tenantInvalid();
        return respond("assessment", authoring.createAssessment(tenantId, body));
    }

    @PutMapping("/assessments/{assessmentId}")
    public ResponseEntity<Map<String, Object>> updateAssessment(
            @PathVariable String assessmentId,
            @RequestBody(required = false) FundoAuthoringService.AssessmentUpsert body) {
        UUID tenantId = currentTenant();
        UUID aid = FundoV11Support.tryParseUuid(assessmentId);
        if (tenantId == null || aid == null) {
            return FundoV11Support.notFound("ASSESSMENT_NOT_FOUND", "Assessment not found");
        }
        return respond("assessment", authoring.updateAssessment(tenantId, aid,
                body == null ? new FundoAuthoringService.AssessmentUpsert(
                        null, null, null, null, null, null, null, null) : body));
    }

    @PostMapping("/assessments/{assessmentId}/questions")
    public ResponseEntity<Map<String, Object>> addQuestion(
            @PathVariable String assessmentId,
            @RequestBody(required = false) FundoAuthoringService.QuestionUpsert body) {
        UUID tenantId = currentTenant();
        UUID aid = FundoV11Support.tryParseUuid(assessmentId);
        if (tenantId == null || aid == null) {
            return FundoV11Support.notFound("ASSESSMENT_NOT_FOUND", "Assessment not found");
        }
        return respond("question", authoring.addQuestion(tenantId, aid, body));
    }

    @PutMapping("/assessments/{assessmentId}/questions/{questionId}")
    public ResponseEntity<Map<String, Object>> updateQuestion(
            @PathVariable String assessmentId,
            @PathVariable String questionId,
            @RequestBody(required = false) FundoAuthoringService.QuestionUpsert body) {
        UUID tenantId = currentTenant();
        UUID aid = FundoV11Support.tryParseUuid(assessmentId);
        UUID qid = FundoV11Support.tryParseUuid(questionId);
        if (tenantId == null || aid == null || qid == null) {
            return FundoV11Support.notFound("QUESTION_NOT_FOUND", "Question not found");
        }
        return respond("question", authoring.updateQuestion(tenantId, aid, qid, body));
    }

    // ── helpers ──────────────────────────────────────────────

    private static UUID currentTenant() {
        RequestContext ctx = RequestContextHolder.require();
        return FundoV11Support.requireTenantOrNull(ctx);
    }

    private static ResponseEntity<Map<String, Object>> tenantInvalid() {
        return FundoV11Support.badRequest("TENANT_INVALID", "X-Tenant-ID is required and must be a UUID");
    }

    private static ResponseEntity<Map<String, Object>> respond(
            String key, AuthoringResult<Map<String, Object>> result) {
        return switch (result.kind()) {
            case OK -> FundoV11Support.dataEnvelope(key, result.value());
            case BAD_REQUEST -> FundoV11Support.badRequest(result.code(), result.message());
            case NOT_FOUND -> FundoV11Support.notFound(result.code(), result.message());
            case CONFLICT -> FundoV11Support.conflict(result.code(), result.message());
        };
    }
}
