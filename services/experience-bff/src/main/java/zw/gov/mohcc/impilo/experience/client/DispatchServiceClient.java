package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for the dispatch sovereign service (port 8302).
 * Manages task dispatch, assignment, and completion.
 */
@Component
public class DispatchServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DispatchServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DispatchServiceClient(RestTemplate serviceRestTemplate,
                                 ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.dispatchBaseUrl();
    }

    public JsonNode listTasks(String assigneeId, String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/dispatch/tasks");
        if (assigneeId != null && !assigneeId.isBlank()) {
            b.queryParam("assigneeId", assigneeId);
        }
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        String url = b.toUriString();
        log.debug("Dispatch: listTasks [assigneeId={}, status={}]", assigneeId, status);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getTask(String id) {
        String url = baseUrl + "/internal/v1/dispatch/tasks/" + id;
        log.debug("Dispatch: getTask id={}", id);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createTask(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/dispatch/tasks";
        log.info("Dispatch: createTask [type={}]", body.get("type"));
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode assignTask(String id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/dispatch/tasks/" + id + "/assign";
        log.info("Dispatch: assignTask id={} assignee={}", id, body.get("assigneeId"));
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PATCH, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode completeTask(String id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/dispatch/tasks/" + id + "/complete";
        log.info("Dispatch: completeTask id={}", id);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PATCH, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
