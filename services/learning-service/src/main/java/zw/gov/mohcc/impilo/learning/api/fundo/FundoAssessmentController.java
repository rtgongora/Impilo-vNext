package zw.gov.mohcc.impilo.learning.api.fundo;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoAssessmentService;

/** Native Fundo assessment surface. */
@RestController
@RequestMapping("/internal/v1/learning/fundo")
public class FundoAssessmentController {

    private final FundoAssessmentService assessmentService;

    public FundoAssessmentController(FundoAssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/assessments/{assessmentId}")
    public ResponseEntity<Map<String, Object>> getAssessment(@PathVariable String assessmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID aid = FundoApiSupport.tryParseUuid(assessmentId);
        if (tenantId == null || aid == null) {
            return FundoApiSupport.notFound("ASSESSMENT_NOT_FOUND", "Assessment not found");
        }
        return assessmentService.getAssessment(tenantId, aid)
                .map(a -> FundoApiSupport.dataEnvelope("assessment", a))
                .orElseGet(() -> FundoApiSupport.notFound("ASSESSMENT_NOT_FOUND", "Assessment not found"));
    }

    @PostMapping("/assessments/{assessmentId}/attempts")
    public ResponseEntity<Map<String, Object>> submitAttempt(
            @PathVariable String assessmentId,
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID aid = FundoApiSupport.tryParseUuid(assessmentId);
        if (tenantId == null || aid == null) {
            return FundoApiSupport.notFound("ASSESSMENT_NOT_FOUND", "Assessment not found");
        }
        if (body == null) {
            return FundoApiSupport.badRequest("INVALID_INPUT", "Request body is required");
        }
        String subjectType = FundoApiSupport.asString(body.get("subjectType"));
        String subjectId = FundoApiSupport.asString(body.get("subjectId"));
        if (subjectType == null || subjectId == null) {
            return FundoApiSupport.badRequest("INVALID_INPUT", "subjectType and subjectId are required");
        }
        UUID enrolmentId = FundoApiSupport.tryParseUuid(FundoApiSupport.asString(body.get("enrolmentId")));
        @SuppressWarnings("unchecked")
        Map<String, Object> answers = (Map<String, Object>) body.get("answers");
        try {
            Map<String, Object> view = assessmentService.submitAttempt(
                    tenantId, aid,
                    new FundoAssessmentService.AttemptSubmission(enrolmentId, subjectType, subjectId, answers));
            return FundoApiSupport.dataEnvelope("attempt", view);
        } catch (IllegalArgumentException ex) {
            return FundoApiSupport.notFound("ASSESSMENT_NOT_FOUND", ex.getMessage());
        } catch (IllegalStateException ex) {
            return FundoApiSupport.conflict("MAX_ATTEMPTS_REACHED", ex.getMessage());
        }
    }

    @GetMapping("/assessments/{assessmentId}/attempts")
    public ResponseEntity<Map<String, Object>> listAttempts(
            @PathVariable String assessmentId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID aid = FundoApiSupport.tryParseUuid(assessmentId);
        if (tenantId == null || aid == null) {
            return FundoApiSupport.dataEnvelope(Map.of("items", java.util.List.of()));
        }
        return FundoApiSupport.dataEnvelope(Map.of(
                "assessmentId", assessmentId,
                "subjectType", subjectType,
                "subjectId", subjectId,
                "items", assessmentService.attemptsForAssessment(tenantId, aid, subjectType, subjectId)));
    }

    @GetMapping("/attempts/{attemptId}")
    public ResponseEntity<Map<String, Object>> getAttempt(@PathVariable String attemptId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID atid = FundoApiSupport.tryParseUuid(attemptId);
        if (tenantId == null || atid == null) {
            return FundoApiSupport.notFound("ATTEMPT_NOT_FOUND", "Attempt not found");
        }
        return assessmentService.getAttempt(tenantId, atid)
                .map(v -> FundoApiSupport.dataEnvelope("attempt", v))
                .orElseGet(() -> FundoApiSupport.notFound("ATTEMPT_NOT_FOUND", "Attempt not found"));
    }

    @GetMapping("/assessments/{assessmentId}/pending-reviews")
    public ResponseEntity<Map<String, Object>> pendingReviews(
            @PathVariable String assessmentId,
            @RequestParam(required = false) Integer limit) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID aid = FundoApiSupport.tryParseUuid(assessmentId);
        if (tenantId == null || aid == null) {
            return FundoApiSupport.dataEnvelope(Map.of("items", java.util.List.of()));
        }
        return FundoApiSupport.dataEnvelope(Map.of(
                "assessmentId", assessmentId,
                "items", assessmentService.listPendingManualReviews(tenantId, aid, limit)));
    }

    @PostMapping("/attempts/{attemptId}/manual-review")
    public ResponseEntity<Map<String, Object>> manualReview(
            @PathVariable String attemptId,
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID atid = FundoApiSupport.tryParseUuid(attemptId);
        if (tenantId == null || atid == null) {
            return FundoApiSupport.notFound("ATTEMPT_NOT_FOUND", "Attempt not found");
        }
        if (body == null) {
            return FundoApiSupport.badRequest("INVALID_INPUT", "Request body is required");
        }
        Integer score = FundoApiSupport.asInteger(body.get("score"));
        Boolean passed = FundoApiSupport.asBoolean(body.get("passed"));
        String reviewerId = FundoApiSupport.asString(body.get("reviewerId"));
        String rubricAppliedJson = FundoApiSupport.asString(body.get("rubricAppliedJson"));
        String feedbackText = FundoApiSupport.asString(body.get("feedbackText"));
        return assessmentService.recordManualReview(
                        tenantId, atid,
                        new FundoAssessmentService.ManualReviewUpdate(
                                score, passed, reviewerId, rubricAppliedJson, feedbackText))
                .map(v -> FundoApiSupport.dataEnvelope("attempt", v))
                .orElseGet(() -> FundoApiSupport.notFound("ATTEMPT_NOT_FOUND", "Attempt not found"));
    }
}
