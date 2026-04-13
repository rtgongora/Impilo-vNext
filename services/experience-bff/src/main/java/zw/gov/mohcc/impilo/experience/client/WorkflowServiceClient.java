package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for the workflow sovereign service (port 8250).
 * Health OS SS5: Workflow, messaging, and event orchestration.
 */
@Component
public class WorkflowServiceClient {

    private static final Logger log = LoggerFactory.getLogger(WorkflowServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public WorkflowServiceClient(RestTemplate serviceRestTemplate,
                                 ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.workflowBaseUrl();
    }

    public JsonNode listWorkflows(String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/workflows");
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        String url = b.toUriString();
        log.debug("Workflow: list [status={}]", status);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getWorkflow(String id) {
        String url = baseUrl + "/internal/v1/workflows/" + id;
        log.debug("Workflow: get id={}", id);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode startWorkflow(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/workflows";
        log.info("Workflow: start [definition={}]", body.get("definitionId"));
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode signalWorkflow(String id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/workflows/" + id + "/signal";
        log.info("Workflow: signal id={}", id);
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listDefinitions() {
        String url = baseUrl + "/internal/v1/workflows/definitions";
        log.debug("Workflow: listDefinitions");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
