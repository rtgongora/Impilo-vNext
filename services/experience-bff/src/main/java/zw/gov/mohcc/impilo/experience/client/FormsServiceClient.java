package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for forms-service (Health OS §11: Extension Points — form schemas).
 *
 * <p>Maps to {@code FormSchemaController} at {@code /internal/v1/forms}.</p>
 */
@Component
public class FormsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FormsServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public FormsServiceClient(RestTemplate serviceRestTemplate,
                              ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.formsBaseUrl();
    }

    public JsonNode createSchema(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/forms";
        log.debug("Forms: create schema");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listSchemas() {
        String url = baseUrl + "/internal/v1/forms";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getSchema(String id) {
        String url = baseUrl + "/internal/v1/forms/" + id;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode createVersion(String id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/forms/" + id + "/versions";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode publishSchema(String id) {
        String url = baseUrl + "/internal/v1/forms/" + id + "/publish";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return response.getBody();
    }

    public JsonNode retireSchema(String id) {
        String url = baseUrl + "/internal/v1/forms/" + id + "/retire";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return response.getBody();
    }

    public JsonNode validatePayload(String id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/forms/" + id + "/validate";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode submitForm(String schemaId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/forms/" + schemaId + "/submissions";
        log.debug("Forms: submit form for schema={}", schemaId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listSubmissions(String schemaId, int page, int size) {
        String url = baseUrl + "/internal/v1/forms/" + schemaId + "/submissions?page=" + page + "&size=" + size;
        log.debug("Forms: list submissions for schema={}", schemaId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }
}
