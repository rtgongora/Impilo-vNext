package zw.gov.mohcc.impilo.learning.api.v11.fundo;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoProgressService;

/** Lesson launch/open endpoint for native Fundo lesson delivery. */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoLessonController {

    private final FundoProgressService progressService;

    public FundoLessonController(FundoProgressService progressService) {
        this.progressService = progressService;
    }

    @PostMapping("/lessons/{lessonId}/open")
    public ResponseEntity<Map<String, Object>> openLesson(
            @PathVariable String lessonId,
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID lid = FundoV11Support.tryParseUuid(lessonId);
        UUID enrolmentId = body == null ? null : FundoV11Support.tryParseUuid(asString(body.get("enrolmentId")));
        if (tenantId == null || lid == null) {
            return FundoV11Support.notFound("LESSON_NOT_FOUND", "Lesson not found");
        }
        try {
            Map<String, Object> view = progressService.openLesson(tenantId, lid, enrolmentId);
            return FundoV11Support.dataEnvelope("progress", view);
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.badRequest("INVALID_INPUT", ex.getMessage());
        } catch (IllegalStateException ex) {
            return FundoV11Support.conflict("ENROLMENT_NOT_ACTIVE", ex.getMessage());
        }
    }

    private static String asString(Object v) { return v == null ? null : v.toString(); }
}
