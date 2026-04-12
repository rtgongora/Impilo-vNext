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
 * HTTP client for the Data Access Governance Service (DAGS).
 *
 * <p>Provides access to governance policy management and the formal
 * access-request / approve / deny workflow that enforces Privacy by
 * Architecture (Health OS Doctrine Brief §6).</p>
 */
@Component
public class DagsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DagsServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DagsServiceClient(RestTemplate serviceRestTemplate,
                             ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.dagsBaseUrl();
    }

    /**
     * List all governance policies for the current tenant.
     */
    public JsonNode listPolicies() {
        String url = baseUrl + "/internal/v1/policies";
        log.info("DAGS: Listing governance policies");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Create a new governance policy.
     */
    public JsonNode createPolicy(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/policies";
        log.info("DAGS: Creating policy [name={}, resourceType={}, effect={}]",
                request.get("name"), request.get("resourceType"), request.get("effect"));
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /**
     * Submit a formal access request for a protected resource.
     */
    public JsonNode submitAccessRequest(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/access-requests";
        log.info("DAGS: Submitting access request [resourceType={}, purpose={}]",
                request.get("resourceType"), request.get("purpose"));
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /**
     * Approve an access request by ID.
     */
    public JsonNode approveAccessRequest(Long id, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/access-requests/" + id + "/approve";
        log.info("DAGS: Approving access request id={}", id);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /**
     * Deny an access request by ID.
     */
    public JsonNode denyAccessRequest(Long id, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/access-requests/" + id + "/deny";
        log.info("DAGS: Denying access request id={}", id);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /**
     * List access requests with optional status filter and pagination.
     */
    public JsonNode listAccessRequests(String status, int page, int size) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/internal/v1/access-requests?page=").append(page)
                .append("&size=").append(size);
        if (status != null && !status.isBlank()) {
            url.append("&status=").append(status);
        }
        log.info("DAGS: Listing access requests [status={}, page={}, size={}]", status, page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
