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
}
