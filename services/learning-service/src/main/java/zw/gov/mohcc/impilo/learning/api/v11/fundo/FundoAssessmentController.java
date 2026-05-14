package zw.gov.mohcc.impilo.learning.api.v11.fundo;

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

/** Native Fundo v1.1 assessment surface (Phase 5B). */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoAssessmentController {

    private final FundoAssessmentService assessmentService;

    public FundoAssessmentController(FundoAssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/assessments/{assessmentId}")
    public ResponseEntity<Map<String, Object>> getAssessment(@PathVariable String assessmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID aid = FundoV11Support.tryParseUuid(assessmentId);
        if (tenantId == null || aid == null) {
            return FundoV11Support.notFound("ASSESSMENT_NOT_FOUND", "Assessment not found");
        }
        return assessmentService.getAssessment(tenantId, aid)
                .map(a -> FundoV11Support.dataEnvelope("assessment", a))
                .orElseGet(() -> FundoV11Support.notFound("ASSESSMENT_NOT_FOUND", "Assessment not found"));
    }

    @PostMapping("/assessments/{assessmentId}/attempts")
    public ResponseEntity<Map<String, Object>> submitAttempt(
            @PathVariable String assessmentId,
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID aid = FundoV11Support.tryParseUuid(assessmentId);
        if (tenantId == null || aid == null) {
            return FundoV11Support.notFound("ASSESSMENT_NOT_FOUND", "Assessment not found");
        }
        if (body == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "Request body is required");
        }
        String subjectType = asString(body.get("subjectType"));
        String subjectId = asString(body.get("subjectId"));
        if (subjectType == null || subjectId == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "subjectType and subjectId are required");
        }
        UUID enrolmentId = FundoV11Support.tryParseUuid(asString(body.get("enrolmentId")));
        @SuppressWarnings("unchecked")
        Map<String, Object> answers = (Map<String, Object>) body.get("answers");
        try {
            Map<String, Object> view = assessmentService.submitAttempt(
                    tenantId, aid,
                    new FundoAssessmentService.AttemptSubmission(enrolmentId, subjectType, subjectId, answers));
            return FundoV11Support.dataEnvelope("attempt", view);
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.notFound("ASSESSMENT_NOT_FOUND", ex.getMessage());
        } catch (IllegalStateException ex) {
            return FundoV11Support.conflict("MAX_ATTEMPTS_REACHED", ex.getMessage());
        }
    }

    @GetMapping("/assessments/{assessmentId}/attempts")
    public ResponseEntity<Map<String, Object>> listAttempts(
            @PathVariable String assessmentId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID aid = FundoV11Support.tryParseUuid(assessmentId);
        if (tenantId == null || aid == null) {
            return FundoV11Support.dataEnvelope(Map.of("items", java.util.List.of()));
        }
        return FundoV11Support.dataEnvelope(Map.of(
                "assessmentId", assessmentId,
                "subjectType", subjectType,
                "subjectId", subjectId,
                "items", assessmentService.attemptsForAssessment(tenantId, aid, subjectType, subjectId)));
    }

    @GetMapping("/attempts/{attemptId}")
    public ResponseEntity<Map<String, Object>> getAttempt(@PathVariable String attemptId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID atid = FundoV11Support.tryParseUuid(attemptId);
        if (tenantId == null || atid == null) {
            return FundoV11Support.notFound("ATTEMPT_NOT_FOUND", "Attempt not found");
        }
        return assessmentService.getAttempt(tenantId, atid)
                .map(v -> FundoV11Support.dataEnvelope("attempt", v))
                .orElseGet(() -> FundoV11Support.notFound("ATTEMPT_NOT_FOUND", "Attempt not found"));
    }

    @GetMapping("/assessments/{assessmentId}/pending-reviews")
    public ResponseEntity<Map<String, Object>> pendingReviews(
            @PathVariable String assessmentId,
            @RequestParam(required = false) Integer limit) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID aid = FundoV11Support.tryParseUuid(assessmentId);
        if (tenantId == null || aid == null) {
            return FundoV11Support.dataEnvelope(Map.of("items", java.util.List.of()));
        }
        return FundoV11Support.dataEnvelope(Map.of(
                "assessmentId", assessmentId,
                "items", assessmentService.listPendingManualReviews(tenantId, aid, limit)));
    }

    @PostMapping("/attempts/{attemptId}/manual-review")
    public ResponseEntity<Map<String, Object>> manualReview(
            @PathVariable String attemptId,
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID atid = FundoV11Support.tryParseUuid(attemptId);
        if (tenantId == null || atid == null) {
            return FundoV11Support.notFound("ATTEMPT_NOT_FOUND", "Attempt not found");
        }
        if (body == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "Request body is required");
        }
        Integer score = asInteger(body.get("score"));
        Boolean passed = asBoolean(body.get("passed"));
        String reviewerId = asString(body.get("reviewerId"));
        String rubricAppliedJson = asString(body.get("rubricAppliedJson"));
        String feedbackText = asString(body.get("feedbackText"));
        return assessmentService.recordManualReview(
                        tenantId, atid,
                        new FundoAssessmentService.ManualReviewUpdate(
                                score, passed, reviewerId, rubricAppliedJson, feedbackText))
                .map(v -> FundoV11Support.dataEnvelope("attempt", v))
                .orElseGet(() -> FundoV11Support.notFound("ATTEMPT_NOT_FOUND", "Attempt not found"));
    }

    private static String asString(Object v) { return v == null ? null : v.toString(); }
    private static Integer asInteger(Object v) {
        if (v == null) return null;
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    private static Boolean asBoolean(Object v) {
        if (v == null) return null;
        return Boolean.parseBoolean(v.toString());
    }
}
