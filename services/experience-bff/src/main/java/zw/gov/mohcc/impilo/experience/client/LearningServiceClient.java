package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.LearningServiceRuntimeProperties;

@Component
public class LearningServiceClient {

    private static final Logger log = LoggerFactory.getLogger(LearningServiceClient.class);

    private final RestTemplate restTemplate;
    private final LearningServiceRuntimeProperties props;

    public LearningServiceClient(RestTemplate serviceRestTemplate, LearningServiceRuntimeProperties props) {
        this.restTemplate = serviceRestTemplate;
        this.props = props;
    }

    public boolean isConfigured() {
        return props.isConfigured();
    }

    public JsonNode getWorkflowContext(
            String appCode, String routeRef, String workflowCode, String roles, String subjectType, String subjectId) {
        if (!props.isConfigured()) {
            return null;
        }
        UriComponentsBuilder b =
                UriComponentsBuilder.fromHttpUrl(trim(props.getBaseUrl()) + "/internal/v1/learning/workflow-context")
                        .queryParam("appCode", appCode)
                        .queryParam("routeRef", routeRef);
        if (workflowCode != null && !workflowCode.isBlank()) {
            b.queryParam("workflowCode", workflowCode);
        }
        if (roles != null && !roles.isBlank()) {
            b.queryParam("roles", roles);
        }
        if (subjectType != null && !subjectType.isBlank()) {
            b.queryParam("subjectType", subjectType);
        }
        if (subjectId != null && !subjectId.isBlank()) {
            b.queryParam("subjectId", subjectId);
        }
        try {
            ResponseEntity<JsonNode> res = restTemplate.getForEntity(b.toUriString(), JsonNode.class);
            return unwrapData(res.getBody());
        } catch (Exception e) {
            log.debug("Learning workflow-context failed: {}", e.getMessage());
            return null;
        }
    }

    public JsonNode getHelpdeskLearning(String issueType, String subjectType, String subjectId) {
        if (!props.isConfigured()) {
            return null;
        }
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(
                        trim(props.getBaseUrl()) + "/internal/v1/learning/helpdesk/" + issueType);
        if (subjectType != null && !subjectType.isBlank()) {
            b.queryParam("subjectType", subjectType);
        }
        if (subjectId != null && !subjectId.isBlank()) {
            b.queryParam("subjectId", subjectId);
        }
        try {
            ResponseEntity<JsonNode> res = restTemplate.getForEntity(b.toUriString(), JsonNode.class);
            return unwrapData(res.getBody());
        } catch (Exception e) {
            log.debug("Learning helpdesk fetch failed: {}", e.getMessage());
            return null;
        }
    }

    public JsonNode searchHits(String q, int limit) {
        if (!props.isConfigured()) {
            return null;
        }
        String url = UriComponentsBuilder.fromHttpUrl(trim(props.getBaseUrl()) + "/internal/v1/learning/search-hits")
                .queryParam("q", q)
                .queryParam("limit", limit)
                .toUriString();
        try {
            ResponseEntity<JsonNode> res = restTemplate.getForEntity(url, JsonNode.class);
            return unwrapData(res.getBody());
        } catch (Exception e) {
            log.debug("Learning search-hits failed: {}", e.getMessage());
            return null;
        }
    }

    public JsonNode postResourceOpened(Map<String, Object> body) {
        if (!props.isConfigured()) {
            return null;
        }
        String url = trim(props.getBaseUrl()) + "/internal/v1/learning/resource-opened";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<JsonNode> res = restTemplate.postForEntity(url, entity, JsonNode.class);
            return unwrapData(res.getBody());
        } catch (Exception e) {
            log.debug("Learning resource-opened failed: {}", e.getMessage());
            return null;
        }
    }

    public JsonNode getSubjectCompletions(String subjectType, String subjectId, int limit) {
        if (!props.isConfigured()) {
            return null;
        }
        String url = UriComponentsBuilder.fromHttpUrl(trim(props.getBaseUrl()) + "/internal/v1/learning/subject-completions")
                .queryParam("subjectType", subjectType)
                .queryParam("subjectId", subjectId)
                .queryParam("limit", limit)
                .toUriString();
        try {
            ResponseEntity<JsonNode> res = restTemplate.getForEntity(url, JsonNode.class);
            return unwrapData(res.getBody());
        } catch (Exception e) {
            log.debug("Learning subject-completions failed: {}", e.getMessage());
            return null;
        }
    }

    public JsonNode getResource(String resourceId) {
        if (!props.isConfigured()) {
            return null;
        }
        String url = trim(props.getBaseUrl()) + "/internal/v1/learning/resources/" + resourceId;
        try {
            ResponseEntity<JsonNode> res = restTemplate.getForEntity(url, JsonNode.class);
            return unwrapData(res.getBody());
        } catch (Exception e) {
            log.debug("Learning resource get failed: {}", e.getMessage());
            return null;
        }
    }

    public JsonNode postSubjectProfile(Map<String, Object> body) {
        if (!props.isConfigured()) {
            return null;
        }
        String url = trim(props.getBaseUrl()) + "/internal/v1/learning/subject-profile";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<JsonNode> res = restTemplate.postForEntity(url, entity, JsonNode.class);
            return unwrapData(res.getBody());
        } catch (Exception e) {
            log.debug("Learning subject-profile failed: {}", e.getMessage());
            return null;
        }
    }

    private static JsonNode unwrapData(JsonNode root) {
        if (root != null && root.has("data") && !root.get("data").isNull()) {
            return root.get("data");
        }
        return null;
    }

    private static String trim(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
}
