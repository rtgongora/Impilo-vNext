package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * HTTP client for forms-service and rules-service (Health OS §11: Extension Points).
 * Forms = versioned form definitions for clinical/operational workflows.
 * Rules = business rule evaluation engine.
 */
@Component
public class ExtensionServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ExtensionServiceClient.class);

    private final RestTemplate restTemplate;
    private final String formsBaseUrl;
    private final String rulesBaseUrl;

    public ExtensionServiceClient(RestTemplate serviceRestTemplate,
                                   ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.formsBaseUrl = endpoints.formsBaseUrl();
        this.rulesBaseUrl = endpoints.rulesBaseUrl();
    }

    // ── Forms ────────────────────────────────────────────────────────

    public JsonNode listForms(String tenantId, int page, int size) {
        String url = formsBaseUrl + "/internal/v1/forms?tenantId=" + tenantId + "&page=" + page + "&size=" + size;
        log.debug("Forms: listing page={} size={}", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getForm(String formId) {
        String url = formsBaseUrl + "/internal/v1/forms/" + formId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    // ── Rules ────────────────────────────────────────────────────────

    public JsonNode listRules(String tenantId, int page, int size) {
        String url = rulesBaseUrl + "/internal/v1/rules?tenantId=" + tenantId + "&page=" + page + "&size=" + size;
        log.debug("Rules: listing page={} size={}", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode evaluateRule(String ruleId, String factsJson) {
        String url = rulesBaseUrl + "/internal/v1/rules/" + ruleId + "/evaluate";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, factsJson, JsonNode.class);
        return response.getBody();
    }
}
