package zw.gov.mohcc.impilo.learning.api.fundo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoProgressService;

/**
 * Native Fundo progress surface. Progress writes emit outbox events via
 * {@code FundoOutboxAppender}.
 */
@RestController
@RequestMapping("/internal/v1/learning/fundo")
public class FundoProgressController {

    private final FundoProgressService progressService;

    public FundoProgressController(FundoProgressService progressService) {
        this.progressService = progressService;
    }

    @PostMapping("/progress")
    public ResponseEntity<Map<String, Object>> recordProgress(
            @RequestBody(required = false) Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireLearnerAccess();
        if (forbidden != null) return forbidden;
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        if (tenantId == null || body == null) {
            return FundoApiSupport.badRequest("INVALID_INPUT",
                    tenantId == null ? "Tenant header is not a valid UUID" : "Request body is required");
        }
        UUID enrolmentId = FundoApiSupport.tryParseUuid(FundoApiSupport.asString(body.get("enrolmentId")));
        if (enrolmentId == null) {
            return FundoApiSupport.badRequest("INVALID_INPUT", "enrolmentId is required and must be a UUID");
        }
        UUID moduleId = FundoApiSupport.tryParseUuid(FundoApiSupport.asString(body.get("moduleId")));
        UUID lessonId = FundoApiSupport.tryParseUuid(FundoApiSupport.asString(body.get("lessonId")));
        Object percentRaw = body.get("progressPercent");
        int percent;
        if (percentRaw instanceof Number n) percent = n.intValue();
        else {
            try {
                percent = Integer.parseInt(FundoApiSupport.asString(percentRaw));
            } catch (RuntimeException ex) {
                return FundoApiSupport.badRequest("INVALID_INPUT", "progressPercent must be an integer 0-100");
            }
        }
        Integer score = null;
        Object scoreRaw = body.get("score");
        if (scoreRaw instanceof Number n) score = n.intValue();

        try {
            Map<String, Object> view = progressService.recordProgress(
                    tenantId,
                    new FundoProgressService.ProgressUpdate(enrolmentId, moduleId, lessonId, percent, score));
            return FundoApiSupport.dataEnvelope("progress", view);
        } catch (IllegalArgumentException ex) {
            return FundoApiSupport.notFound("NOT_FOUND", ex.getMessage());
        } catch (IllegalStateException ex) {
            return FundoApiSupport.conflict("ENROLMENT_NOT_ACTIVE", ex.getMessage());
        }
    }

    @GetMapping("/progress")
    public ResponseEntity<Map<String, Object>> listProgress(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireLearnerAccess();
        if (forbidden != null) return forbidden;
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subjectType", subjectType);
        data.put("subjectId", subjectId);
        if (tenantId == null || subjectType == null || subjectId == null) {
            data.put("items", List.of());
            return FundoApiSupport.dataEnvelope(data);
        }
        data.put("items", progressService.listForSubject(tenantId, subjectType, subjectId));
        return FundoApiSupport.dataEnvelope(data);
    }
}
