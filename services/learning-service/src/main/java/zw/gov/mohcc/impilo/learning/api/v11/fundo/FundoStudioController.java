package zw.gov.mohcc.impilo.learning.api.v11.fundo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.core.LearningV11NativeService;

@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoStudioController {

    private static final UUID DEFAULT_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final LearningV11NativeService service;

    public FundoStudioController(LearningV11NativeService service) {
        this.service = service;
    }

    @GetMapping("/studio/dashboard")
    public ResponseEntity<Map<String, Object>> studioDashboard() {
        return ok(service.studioDashboard(tenantId()));
    }

    @GetMapping("/studio/courses/{courseId}/readiness")
    public ResponseEntity<Map<String, Object>> readiness(@PathVariable String courseId) {
        return ok(service.courseReadiness(tenantId(), courseId));
    }

    @PostMapping("/ai/generate")
    public ResponseEntity<Map<String, Object>> aiGenerate(@RequestBody Map<String, Object> body) {
        return ok(service.aiGenerate(tenantId(), actorId(), body));
    }

    @PostMapping("/nompilo/assist")
    public ResponseEntity<Map<String, Object>> nompiloAssist(@RequestBody Map<String, Object> body) {
        return ok(service.nompiloAssist(body));
    }

    @GetMapping("/library/resources")
    public ResponseEntity<Map<String, Object>> library(@RequestParam(defaultValue = "50") int limit) {
        return ok(service.listLibraryResources(tenantId(), limit));
    }

    @PostMapping("/library/resources")
    public ResponseEntity<Map<String, Object>> createLibrary(@RequestBody Map<String, Object> body) {
        return ok(service.createLibraryResource(tenantId(), actorId(), body));
    }

    @PostMapping("/library/resources/{resourceId}/links")
    public ResponseEntity<Map<String, Object>> linkResource(
            @PathVariable String resourceId,
            @RequestBody Map<String, Object> body) {
        return ok(service.linkLibraryResource(tenantId(), resourceId, body));
    }

    @PostMapping("/library/uploads")
    public ResponseEntity<Map<String, Object>> uploadLibrary(@RequestBody Map<String, Object> body) {
        return ok(service.createLibraryResource(tenantId(), actorId(), body));
    }

    @GetMapping("/media/assets")
    public ResponseEntity<Map<String, Object>> media(@RequestParam(defaultValue = "50") int limit) {
        return ok(service.listMedia(tenantId(), limit));
    }

    @PostMapping("/media/assets")
    public ResponseEntity<Map<String, Object>> createMedia(@RequestBody Map<String, Object> body) {
        return ok(service.createMedia(tenantId(), actorId(), body));
    }

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> notifications(
            @RequestParam String subjectType,
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "50") int limit) {
        return ok(service.listNotifications(tenantId(), subjectType, subjectId, limit));
    }

    @PostMapping("/notifications")
    public ResponseEntity<Map<String, Object>> scheduleNotification(@RequestBody Map<String, Object> body) {
        return ok(service.scheduleNotification(tenantId(), actorId(), body));
    }

    @PostMapping("/interactive/activities")
    public ResponseEntity<Map<String, Object>> createActivity(@RequestBody Map<String, Object> body) {
        return ok(service.createInteractiveActivity(tenantId(), actorId(), body));
    }

    @GetMapping("/interactive/activities")
    public ResponseEntity<Map<String, Object>> listActivities(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String lessonId,
            @RequestParam(defaultValue = "50") int limit) {
        return ok(service.listInteractiveActivities(tenantId(), courseId, lessonId, limit));
    }

    @PostMapping("/interactive/activities/{activityId}/responses")
    public ResponseEntity<Map<String, Object>> submitInteractiveResponse(
            @PathVariable String activityId,
            @RequestBody Map<String, Object> body) {
        return ok(service.submitInteractiveResponse(tenantId(), activityId, body));
    }

    @GetMapping("/interactive/activities/{activityId}/responses")
    public ResponseEntity<Map<String, Object>> responses(
            @PathVariable String activityId,
            @RequestParam(defaultValue = "50") int limit) {
        return ok(service.listInteractiveResponses(tenantId(), activityId, limit));
    }

    @PostMapping("/cohorts")
    public ResponseEntity<Map<String, Object>> createCohort(@RequestBody Map<String, Object> body) {
        return ok(service.createCohort(tenantId(), actorId(), body));
    }

    @GetMapping("/cohorts")
    public ResponseEntity<Map<String, Object>> listCohorts(@RequestParam(defaultValue = "50") int limit) {
        return ok(service.listCohorts(tenantId(), limit));
    }

    @PostMapping("/cohorts/{cohortId}/members")
    public ResponseEntity<Map<String, Object>> addMember(
            @PathVariable String cohortId,
            @RequestBody Map<String, Object> body) {
        return ok(service.addCohortMember(tenantId(), cohortId, actorId(), body));
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody Map<String, Object> body) {
        return ok(service.createScheduledSession(tenantId(), actorId(), body));
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions(@RequestParam(defaultValue = "50") int limit) {
        return ok(service.listScheduledSessions(tenantId(), limit));
    }

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        return ResponseEntity.ok(Map.of("data", data == null ? Map.of() : data));
    }

    private UUID tenantId() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx == null) return DEFAULT_TENANT;
        try {
            return UUID.fromString(ctx.tenantId());
        } catch (Exception ex) {
            return DEFAULT_TENANT;
        }
    }

    private String actorId() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx == null || ctx.principal() == null || ctx.principal().getName() == null) {
            return "system";
        }
        return ctx.principal().getName();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception ex) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "FUNDO_STUDIO_ERROR");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", error));
    }
}
