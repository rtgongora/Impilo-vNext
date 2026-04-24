package zw.gov.mohcc.impilo.learning.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.learning.config.LearningProperties;
import zw.gov.mohcc.impilo.learning.core.LearningPlatformFacade;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;

/**
 * Server-to-server orchestration (Tshepo / Varapi jobs). Secured with
 * {@link #ORCHESTRATION_KEY_HEADER} — restrict at network layer in production.
 */
@RestController
@RequestMapping("/internal/v1/learning/orchestration")
public class LearningOrchestrationController {

    public static final String ORCHESTRATION_KEY_HEADER = "X-Impilo-Learning-Orchestration-Key";

    private static final Logger log = LoggerFactory.getLogger(LearningOrchestrationController.class);

    private final LearningPlatformFacade facade;
    private final LearningProperties learningProperties;
    private final MoodleWebServiceClient moodleWebServiceClient;

    public LearningOrchestrationController(
            LearningPlatformFacade facade,
            LearningProperties learningProperties,
            MoodleWebServiceClient moodleWebServiceClient) {
        this.facade = facade;
        this.learningProperties = learningProperties;
        this.moodleWebServiceClient = moodleWebServiceClient;
    }

    @PostMapping("/assign-path")
    public ResponseEntity<?> assignPath(
            @RequestHeader(value = ORCHESTRATION_KEY_HEADER, required = false) String apiKey,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        if (!authorize(apiKey, request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        try {
            UUID tenantId = UUID.fromString(body.get("tenantId").toString());
            String subjectType = body.get("subjectType").toString();
            String subjectId = body.get("subjectId").toString();
            UUID pathId = UUID.fromString(body.get("pathId").toString());
            String actor = body.get("assignedByActorId") != null ? body.get("assignedByActorId").toString() : "";
            String corr = body.get("correlationId") != null ? body.get("correlationId").toString() : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = body.get("metadata") instanceof Map ? (Map<String, Object>) body.get("metadata") : Map.of();
            Map<String, Object> res = facade.assignLearningPath(tenantId, subjectType, subjectId, pathId, actor, corr, meta);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.warn("assign-path invalid: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register-prerequisite")
    public ResponseEntity<?> registerPrerequisite(
            @RequestHeader(value = ORCHESTRATION_KEY_HEADER, required = false) String apiKey,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        if (!authorize(apiKey, request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        try {
            UUID tenantId = UUID.fromString(body.get("tenantId").toString());
            String subjectType = body.get("subjectType").toString();
            String subjectId = body.get("subjectId").toString();
            String workflowCode = body.get("workflowCode") != null ? body.get("workflowCode").toString() : null;
            UUID resourceId = body.get("resourceId") != null ? UUID.fromString(body.get("resourceId").toString()) : null;
            UUID pathId = body.get("pathId") != null ? UUID.fromString(body.get("pathId").toString()) : null;
            String actor = body.get("actorId") != null ? body.get("actorId").toString() : "";
            String corr = body.get("correlationId") != null ? body.get("correlationId").toString() : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> extra = body.get("extra") instanceof Map ? (Map<String, Object>) body.get("extra") : Map.of();
            Map<String, Object> res = facade.registerLearningPrerequisite(
                    tenantId, subjectType, subjectId, workflowCode, resourceId, pathId, actor, corr, extra);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.warn("register-prerequisite invalid: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/moodle/site-info")
    public ResponseEntity<?> moodleSiteInfo(
            @RequestHeader(value = ORCHESTRATION_KEY_HEADER, required = false) String apiKey,
            HttpServletRequest request) {
        if (!authorize(apiKey, request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        if (!moodleWebServiceClient.isConfigured()) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("error", "moodle_ws_not_configured"));
        }
        Optional<JsonNode> n = moodleWebServiceClient.getSiteInfo();
        if (n.isPresent()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("data", n.get());
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "moodle_call_failed"));
    }

    @PostMapping("/moodle/activity-completion-status")
    public ResponseEntity<?> moodleActivityCompletion(
            @RequestHeader(value = ORCHESTRATION_KEY_HEADER, required = false) String apiKey,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        if (!authorize(apiKey, request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        if (!moodleWebServiceClient.isConfigured()) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("error", "moodle_ws_not_configured"));
        }
        try {
            String courseId = body.get("courseId").toString();
            String moodleUserId = body.get("moodleUserId").toString();
            Optional<JsonNode> n = moodleWebServiceClient.getActivitiesCompletionStatus(courseId, moodleUserId);
            Map<String, Object> envelope = new LinkedHashMap<>();
            if (body.get("tenantId") != null) {
                envelope.put("tenantId", body.get("tenantId").toString());
            }
            if (n.isPresent()) {
                envelope.put("moodle", n.get());
                if (body.get("tenantId") != null
                        && body.get("subjectType") != null
                        && body.get("subjectId") != null) {
                    UUID tenantId = UUID.fromString(body.get("tenantId").toString());
                    String st = body.get("subjectType").toString();
                    String sid = body.get("subjectId").toString();
                    String corr = body.get("correlationId") != null ? body.get("correlationId").toString() : null;
                    Map<String, Object> sync =
                            facade.ingestMoodleActivityCompletionResponse(
                                    tenantId, courseId, moodleUserId, n.get(), st, sid, corr);
                    envelope.put("sync", sync);
                }
                return ResponseEntity.ok(envelope);
            }
            if (body.get("tenantId") != null) {
                try {
                    UUID tenantId = UUID.fromString(body.get("tenantId").toString());
                    facade.recordMoodleWsFailureSnapshot(
                            tenantId,
                            "core_completion_get_activities_completion_status",
                            courseId,
                            moodleUserId,
                            body.get("subjectType") != null ? body.get("subjectType").toString() : null,
                            body.get("subjectId") != null ? body.get("subjectId").toString() : null,
                            body.get("correlationId") != null ? body.get("correlationId").toString() : null,
                            "{\"error\":\"moodle_call_failed\"}");
                } catch (Exception ignored) {
                    // best-effort audit only
                }
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "moodle_call_failed"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private boolean authorize(String apiKey, HttpServletRequest request) {
        byte[] expected = configuredKeyBytes();
        byte[] actual = apiKey == null ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0) {
            log.warn("Rejected orchestration: orchestration key not configured from {}", request.getRemoteAddr());
            return false;
        }
        if (expected.length != actual.length) {
            log.warn("Rejected orchestration: bad key from {}", request.getRemoteAddr());
            return false;
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            log.warn("Rejected orchestration: bad key from {}", request.getRemoteAddr());
            return false;
        }
        return true;
    }

    private byte[] configuredKeyBytes() {
        String k = learningProperties.getIntegration().getOrchestrationInternalKey();
        return k == null ? new byte[0] : k.getBytes(StandardCharsets.UTF_8);
    }
}
