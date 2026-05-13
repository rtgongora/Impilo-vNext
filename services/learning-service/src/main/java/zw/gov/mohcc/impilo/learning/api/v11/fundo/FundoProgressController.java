package zw.gov.mohcc.impilo.learning.api.v11.fundo;

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
 * Native Fundo v1.1 progress surface (Phase 5B). Progress writes always
 * emit a compliant v1.1 outbox event via {@code FundoOutboxAppender}.
 */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoProgressController {

    private final FundoProgressService progressService;

    public FundoProgressController(FundoProgressService progressService) {
        this.progressService = progressService;
    }

    @PostMapping("/progress")
    public ResponseEntity<Map<String, Object>> recordProgress(
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        if (tenantId == null || body == null) {
            return FundoV11Support.badRequest("INVALID_INPUT",
                    tenantId == null ? "Tenant header is not a valid UUID" : "Request body is required");
        }
        UUID enrolmentId = FundoV11Support.tryParseUuid(asString(body.get("enrolmentId")));
        if (enrolmentId == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "enrolmentId is required and must be a UUID");
        }
        UUID moduleId = FundoV11Support.tryParseUuid(asString(body.get("moduleId")));
        UUID lessonId = FundoV11Support.tryParseUuid(asString(body.get("lessonId")));
        Object percentRaw = body.get("progressPercent");
        int percent;
        if (percentRaw instanceof Number n) percent = n.intValue();
        else {
            try {
                percent = Integer.parseInt(asString(percentRaw));
            } catch (RuntimeException ex) {
                return FundoV11Support.badRequest("INVALID_INPUT", "progressPercent must be an integer 0-100");
            }
        }
        Integer score = null;
        Object scoreRaw = body.get("score");
        if (scoreRaw instanceof Number n) score = n.intValue();

        try {
            Map<String, Object> view = progressService.recordProgress(
                    tenantId,
                    new FundoProgressService.ProgressUpdate(enrolmentId, moduleId, lessonId, percent, score));
            return FundoV11Support.dataEnvelope("progress", view);
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.notFound("NOT_FOUND", ex.getMessage());
        } catch (IllegalStateException ex) {
            return FundoV11Support.conflict("ENROLMENT_NOT_ACTIVE", ex.getMessage());
        }
    }

    @GetMapping("/progress")
    public ResponseEntity<Map<String, Object>> listProgress(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subjectType", subjectType);
        data.put("subjectId", subjectId);
        if (tenantId == null || subjectType == null || subjectId == null) {
            data.put("items", List.of());
            return FundoV11Support.dataEnvelope(data);
        }
        data.put("items", progressService.listForSubject(tenantId, subjectType, subjectId));
        return FundoV11Support.dataEnvelope(data);
    }

    private static String asString(Object v) { return v == null ? null : v.toString(); }
}
