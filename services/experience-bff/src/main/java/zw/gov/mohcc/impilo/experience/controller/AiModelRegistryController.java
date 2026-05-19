package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.AiModelRegistryServiceClient;

import java.util.Map;

@RestController
@RequestMapping({"/internal/v1/ai", "/internal/v1/ai-governance"})
public class AiModelRegistryController {

    private static final Logger log = LoggerFactory.getLogger(AiModelRegistryController.class);
    private final AiModelRegistryServiceClient aiClient;

    public AiModelRegistryController(AiModelRegistryServiceClient aiClient) {
        this.aiClient = aiClient;
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> listModels(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            JsonNode data = aiClient.listModels();
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            return unavailable(requestId, correlationId, e);
        }
    }

    @GetMapping("/models/{modelId}")
    public ResponseEntity<Map<String, Object>> getModel(
            @PathVariable String modelId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = aiClient.getModel(modelId);
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            return unavailable(requestId, correlationId, e);
        }
    }

    @PostMapping("/models")
    public ResponseEntity<Map<String, Object>> createModel(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = aiClient.createModel(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(data, requestId, correlationId));
        } catch (Exception e) {
            return unavailable(requestId, correlationId, e);
        }
    }

    @PostMapping("/models/{modelId}/approve")
    public ResponseEntity<Map<String, Object>> approveModel(
            @PathVariable String modelId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = aiClient.approveModel(modelId, body == null ? Map.of() : body);
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            return unavailable(requestId, correlationId, e);
        }
    }

    @PostMapping("/models/{modelId}/withdraw")
    public ResponseEntity<Map<String, Object>> withdrawModel(
            @PathVariable String modelId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = aiClient.withdrawModel(modelId, body == null ? Map.of() : body);
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            return unavailable(requestId, correlationId, e);
        }
    }

    @PostMapping("/models/{modelId}/versions")
    public ResponseEntity<Map<String, Object>> createVersion(
            @PathVariable String modelId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = aiClient.createModelVersion(modelId, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(data, requestId, correlationId));
        } catch (Exception e) {
            return unavailable(requestId, correlationId, e);
        }
    }

    @GetMapping("/inference-records")
    public ResponseEntity<Map<String, Object>> listInferenceRecords(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "model_id") String modelId,
            @RequestParam(required = false, name = "workflow_ref") String workflowRef,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            JsonNode data = aiClient.listInferenceRecords(modelId, workflowRef);
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            return unavailable(requestId, correlationId, e);
        }
    }

    @PostMapping("/inference-records")
    public ResponseEntity<Map<String, Object>> recordInference(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = aiClient.recordInference(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(data, requestId, correlationId));
        } catch (Exception e) {
            return unavailable(requestId, correlationId, e);
        }
    }

    @GetMapping("/drift-events")
    public ResponseEntity<Map<String, Object>> listDriftEvents(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "model_id") String modelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (modelId == null || modelId.isBlank()) {
            return badRequest("MODEL_ID_REQUIRED", "model_id is required for drift events", requestId, correlationId);
        }
        try {
            JsonNode data = aiClient.listDriftEvents(modelId);
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            return unavailable(requestId, correlationId, e);
        }
    }

    @PostMapping("/drift-events")
    public ResponseEntity<Map<String, Object>> recordDriftEvent(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        Object modelId = body.get("model_id");
        if (modelId == null || modelId.toString().isBlank()) {
            return badRequest("MODEL_ID_REQUIRED", "model_id is required for drift events", requestId, correlationId);
        }
        try {
            JsonNode data = aiClient.recordDriftEvent(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(data, requestId, correlationId));
        } catch (Exception e) {
            return unavailable(requestId, correlationId, e);
        }
    }

    private ResponseEntity<Map<String, Object>> ok(JsonNode data, String requestId, String correlationId) {
        return ResponseEntity.ok(envelope(data, requestId, correlationId));
    }

    private Map<String, Object> envelope(JsonNode data, String requestId, String correlationId) {
        if (data == null || data.isNull()) {
            throw new IllegalStateException("ai-model-registry-service returned empty response body");
        }
        return Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId));
    }

    private ResponseEntity<Map<String, Object>> badRequest(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> unavailable(String requestId, String correlationId, Exception e) {
        log.warn("AI model registry route unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of(
                        "code", "AI_MODEL_REGISTRY_UNAVAILABLE",
                        "message", "AI model registry upstream is unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
