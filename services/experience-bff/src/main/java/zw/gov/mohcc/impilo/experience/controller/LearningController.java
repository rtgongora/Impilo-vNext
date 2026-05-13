package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.LearningServiceClient;

@RestController
@RequestMapping("/internal/v1/learning")
public class LearningController {

    private final LearningServiceClient learningClient;

    public LearningController(LearningServiceClient learningClient) {
        this.learningClient = learningClient;
    }

    @GetMapping("/workflow-context")
    public ResponseEntity<Map<String, Object>> workflowContext(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String appCode,
            @RequestParam String routeRef,
            @RequestParam(required = false) String workflowCode,
            @RequestParam(required = false) String roles,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId) {
        JsonNode n = learningClient.getWorkflowContext(appCode, routeRef, workflowCode, roles, subjectType, subjectId);
        JsonNode payload = n != null ? n : JsonNodeFactory.instance.objectNode();
        return ResponseEntity.ok(Map.of("data", payload));
    }

    @GetMapping("/helpdesk/{issueType}")
    public ResponseEntity<Map<String, Object>> helpdesk(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String issueType,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId) {
        JsonNode n = learningClient.getHelpdeskLearning(issueType, subjectType, subjectId);
        JsonNode payload = n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode());
        return ResponseEntity.ok(Map.of("data", payload));
    }

    @PostMapping("/resource-opened")
    public ResponseEntity<Map<String, Object>> resourceOpened(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId, @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postResourceOpened(body);
        JsonNode payload = n != null ? n : JsonNodeFactory.instance.objectNode().put("status", "skipped");
        return ResponseEntity.ok(Map.of("data", payload));
    }

    @GetMapping("/subject-completions")
    public ResponseEntity<Map<String, Object>> subjectCompletions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "25") int limit) {
        JsonNode n = learningClient.getSubjectCompletions(subjectType, subjectId, limit);
        JsonNode payload = n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode());
        return ResponseEntity.ok(Map.of("data", payload));
    }

    @GetMapping("/resources/{resourceId}")
    public ResponseEntity<Map<String, Object>> resource(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId, @PathVariable String resourceId) {
        JsonNode n = learningClient.getResource(resourceId);
        if (n == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("data", n));
    }

    @PostMapping("/subject-profile")
    public ResponseEntity<Map<String, Object>> subjectProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId, @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postSubjectProfile(body);
        JsonNode payload = n != null ? n : JsonNodeFactory.instance.objectNode().put("status", "skipped");
        return ResponseEntity.ok(Map.of("data", payload));
    }

    /**
     * Phase 6B — One Experience Shell delivery layer BFF surface for the
     * Phase 5B native Fundo catalogue. Thin passthrough that forwards the
     * tenant context and the optional filters to {@code learning-service},
     * returns the {@code {"data": ...}} envelope unmodified, and falls back
     * to an empty items list when the upstream is unavailable so the shell
     * can render a graceful empty state instead of a hard failure.
     */
    @GetMapping("/v11/catalog")
    public ResponseEntity<Map<String, Object>> v11Catalog(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Boolean cpdEligible,
            @RequestParam(required = false) Boolean mandatory,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "25") int limit) {
        JsonNode n = learningClient.getV11Catalog(status, category, level, cpdEligible, mandatory, language, limit);
        if (n != null) {
            return ResponseEntity.ok(Map.of("data", n));
        }
        JsonNodeFactory f = JsonNodeFactory.instance;
        return ResponseEntity.ok(Map.of(
                "data", f.objectNode().put("limit", limit).set("items", f.arrayNode())));
    }

    @GetMapping("/v11/catalog/{courseId}")
    public ResponseEntity<Map<String, Object>> v11Course(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId) {
        JsonNode n = learningClient.getV11Course(courseId);
        if (n == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("data", n));
    }

    @GetMapping("/v11/courses/{courseId}/structure")
    public ResponseEntity<Map<String, Object>> v11CourseStructure(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId) {
        JsonNode n = learningClient.getV11CourseStructure(courseId);
        if (n == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("data", n));
    }
}
