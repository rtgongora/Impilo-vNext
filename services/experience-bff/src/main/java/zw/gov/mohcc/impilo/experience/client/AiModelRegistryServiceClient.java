package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for ai-model-registry-service (Health Intelligence Plane).
 *
 * <p>Provides access to AI model governance, inference records,
 * drift events, and model version management.</p>
 */
@Component
public class AiModelRegistryServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiModelRegistryServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AiModelRegistryServiceClient(RestTemplate serviceRestTemplate,
                                         ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.aiModelRegistryBaseUrl();
    }

    // ── Models ──────────────────────────────────────────────────────────

    public JsonNode listModels() {
        String url = baseUrl + "/internal/v1/ai-registry/models";
        log.info("AI-REGISTRY: Listing models");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getModel(String modelId) {
        String url = baseUrl + "/internal/v1/ai-registry/models/" + modelId;
        log.info("AI-REGISTRY: Getting model={}", modelId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createModel(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/ai-registry/models";
        log.info("AI-REGISTRY: Creating model [name={}]", request.get("name"));
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateModel(String modelId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/ai-registry/models/" + modelId;
        log.info("AI-REGISTRY: Updating model={}", modelId);
        restTemplate.put(url, new HttpEntity<>(request));
        return getModel(modelId);
    }

    public JsonNode approveModel(String modelId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/ai-registry/models/" + modelId + "/approve";
        log.info("AI-REGISTRY: Approving model={}", modelId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode withdrawModel(String modelId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/ai-registry/models/" + modelId + "/withdraw";
        log.info("AI-REGISTRY: Withdrawing model={}", modelId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    // ── Model Versions ──────────────────────────────────────────────────

    public JsonNode listModelVersions(String modelId) {
        String url = baseUrl + "/internal/v1/ai-registry/models/" + modelId + "/versions";
        log.info("AI-REGISTRY: Listing versions for model={}", modelId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createModelVersion(String modelId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/ai-registry/models/" + modelId + "/versions";
        log.info("AI-REGISTRY: Creating version for model={}", modelId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    // ── Inference Records ───────────────────────────────────────────────

    public JsonNode listInferenceRecords(String modelId, String workflowRef) {
        StringBuilder url = new StringBuilder(baseUrl + "/internal/v1/ai-registry/inference-records?");
        if (modelId != null) url.append("model_id=").append(modelId).append("&");
        if (workflowRef != null) url.append("workflow_ref=").append(workflowRef);
        log.info("AI-REGISTRY: Listing inference records [model={}, workflow={}]", modelId, workflowRef);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordInference(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/ai-registry/inference-records";
        log.info("AI-REGISTRY: Recording inference [model={}, class={}]",
                request.get("model_id"), request.get("inference_class"));
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    // ── Drift Events ────────────────────────────────────────────────────

    public JsonNode listDriftEvents(String modelId) {
        String url = baseUrl + "/internal/v1/ai-registry/drift-events?model_id=" + modelId;
        log.info("AI-REGISTRY: Listing drift events for model={}", modelId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordDriftEvent(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/ai-registry/drift-events";
        log.info("AI-REGISTRY: Recording drift event [model={}, metric={}]",
                request.get("model_id"), request.get("drift_metric"));
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
