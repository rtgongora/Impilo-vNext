package zw.gov.mohcc.impilo.learning.api.fundo;

import java.util.Map;
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
import zw.gov.mohcc.impilo.learning.core.LearningNativeService;
import zw.gov.mohcc.impilo.learning.fundo.FundoLiveCompletionService;

@RestController
@RequestMapping("/internal/v1/learning/fundo")
public class FundoStudioController {

    private final LearningNativeService service;
    private final FundoLiveCompletionService liveCompletionService;

    public FundoStudioController(LearningNativeService service,
                                 FundoLiveCompletionService liveCompletionService) {
        this.service = service;
        this.liveCompletionService = liveCompletionService;
    }

    @GetMapping("/studio/dashboard")
    public ResponseEntity<Map<String, Object>> studioDashboard() {
        return FundoApiSupport.dataEnvelope(service.studioDashboard(tenantId()));
    }

    @GetMapping("/studio/courses/{courseId}/readiness")
    public ResponseEntity<Map<String, Object>> readiness(@PathVariable String courseId) {
        return FundoApiSupport.dataEnvelope(service.courseReadiness(tenantId(), courseId));
    }

    @PostMapping("/ai/generate")
    public ResponseEntity<Map<String, Object>> aiGenerate(@RequestBody Map<String, Object> body) {
        return FundoApiSupport.dataEnvelope(service.aiGenerate(tenantId(), actorId(), body));
    }

    @PostMapping("/nompilo/assist")
    public ResponseEntity<Map<String, Object>> nompiloAssist(@RequestBody Map<String, Object> body) {
        return FundoApiSupport.dataEnvelope(service.nompiloAssist(body));
    }

    @GetMapping("/library/resources")
    public ResponseEntity<Map<String, Object>> library(@RequestParam(defaultValue = "50") int limit) {
        return FundoApiSupport.dataEnvelope(service.listLibraryResources(tenantId(), limit));
    }

    @PostMapping("/library/resources")
    public ResponseEntity<Map<String, Object>> createLibrary(@RequestBody Map<String, Object> body) {
        return FundoApiSupport.dataEnvelope(service.createLibraryResource(tenantId(), actorId(), body));
    }

    @PostMapping("/library/resources/{resourceId}/links")
    public ResponseEntity<Map<String, Object>> linkResource(
            @PathVariable String resourceId,
            @RequestBody Map<String, Object> body) {
        return FundoApiSupport.dataEnvelope(service.linkLibraryResource(tenantId(), resourceId, body));
    }

    @PostMapping("/library/uploads")
    public ResponseEntity<Map<String, Object>> uploadLibrary(@RequestBody Map<String, Object> body) {
        return FundoApiSupport.dataEnvelope(service.createLibraryResource(tenantId(), actorId(), body));
    }

    @GetMapping("/media/assets")
    public ResponseEntity<Map<String, Object>> media(@RequestParam(defaultValue = "50") int limit) {
        return FundoApiSupport.dataEnvelope(service.listMedia(tenantId(), limit));
    }

    @PostMapping("/media/assets")
    public ResponseEntity<Map<String, Object>> createMedia(@RequestBody Map<String, Object> body) {
        return FundoApiSupport.dataEnvelope(service.createMedia(tenantId(), actorId(), body));
    }

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> notifications(
            @RequestParam String subjectType,
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "50") int limit) {
        return FundoApiSupport.dataEnvelope(service.listNotifications(tenantId(), subjectType, subjectId, limit));
    }

    @PostMapping("/notifications")
    public ResponseEntity<Map<String, Object>> scheduleNotification(@RequestBody Map<String, Object> body) {
        return FundoApiSupport.dataEnvelope(service.scheduleNotification(tenantId(), actorId(), body));
    }

    @PostMapping("/interactive/activities")
    public ResponseEntity<Map<String, Object>> createActivity(@RequestBody Map<String, Object> body) {
        return FundoApiSupport.dataEnvelope(service.createInteractiveActivity(tenantId(), actorId(), body));
    }

    @GetMapping("/interactive/activities")
    public ResponseEntity<Map<String, Object>> listActivities(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String lessonId,
            @RequestParam(defaultValue = "50") int limit) {
        return FundoApiSupport.dataEnvelope(service.listInteractiveActivities(tenantId(), courseId, lessonId, limit));
    }

    @PostMapping("/interactive/activities/{activityId}/responses")
    public ResponseEntity<Map<String, Object>> submitInteractiveResponse(
            @PathVariable String activityId,
            @RequestBody Map<String, Object> body) {
        return FundoApiSupport.dataEnvelope(service.submitInteractiveResponse(tenantId(), activityId, body));
    }

    @GetMapping("/interactive/activities/{activityId}/responses")
    public ResponseEntity<Map<String, Object>> responses(
            @PathVariable String activityId,
            @RequestParam(defaultValue = "50") int limit) {
        return FundoApiSupport.dataEnvelope(service.listInteractiveResponses(tenantId(), activityId, limit));
    }

    @PostMapping("/cohorts")
    public ResponseEntity<Map<String, Object>> createCohort(@RequestBody Map<String, Object> body) {
        return FundoApiSupport.dataEnvelope(service.createCohort(tenantId(), actorId(), body));
    }

    @GetMapping("/cohorts")
    public ResponseEntity<Map<String, Object>> listCohorts(@RequestParam(defaultValue = "50") int limit) {
        return FundoApiSupport.dataEnvelope(service.listCohorts(tenantId(), limit));
    }

    @PostMapping("/cohorts/{cohortId}/members")
    public ResponseEntity<Map<String, Object>> addMember(
            @PathVariable String cohortId,
            @RequestBody Map<String, Object> body) {
        return FundoApiSupport.dataEnvelope(service.addCohortMember(tenantId(), cohortId, actorId(), body));
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody Map<String, Object> body) {
        return FundoApiSupport.dataEnvelope(service.createScheduledSession(tenantId(), actorId(), body));
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions(@RequestParam(defaultValue = "50") int limit) {
        return FundoApiSupport.dataEnvelope(service.listScheduledSessions(tenantId(), limit));
    }

    @PostMapping("/sessions/live-completion")
    public ResponseEntity<Map<String, Object>> recordLiveCompletion(@RequestBody Map<String, Object> body) {
        return FundoApiSupport.dataEnvelope(liveCompletionService.recordLiveCompletion(tenantId(), body));
    }

    private java.util.UUID tenantId() {
        return FundoApiSupport.currentTenantOrDefault();
    }

    private String actorId() {
        return FundoApiSupport.currentActorOrSystem();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception ex) {
        return FundoApiSupport.error(HttpStatus.BAD_REQUEST, "FUNDO_STUDIO_ERROR", ex.getMessage());
    }
}
