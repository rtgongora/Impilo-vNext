package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the workforce-governance-service Platform Origin API
 * ({@code /internal/v1/platform-origin/**}: country operations, two-person
 * platform action approvals/execution, national appointments).
 *
 * <p>Resolves the same {@code impilo.services.workforce-governance-base-url}
 * property as {@link WorkforceGovernanceClient} (via
 * {@link ServiceClientConfig.ServiceEndpoints#workforceGovernanceBaseUrl()}).
 * Methods throw on any non-2xx/empty upstream response so callers fail closed
 * (the BFF controller translates to HTTP 502).</p>
 */
@Component
public class PlatformOriginGovernanceClient {

    private static final Logger log = LoggerFactory.getLogger(PlatformOriginGovernanceClient.class);
    private static final String BASE_PATH = "/internal/v1/platform-origin";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PlatformOriginGovernanceClient(RestTemplate serviceRestTemplate,
                                          ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.workforceGovernanceBaseUrl();
    }

    public JsonNode initiateCountryOperation(Map<String, Object> body) {
        return post(BASE_PATH + "/country-operations", body);
    }

    public JsonNode approveAction(UUID accessRequestId, Map<String, Object> body) {
        return post(BASE_PATH + "/actions/" + accessRequestId + "/approve", body);
    }

    public JsonNode executeAction(UUID accessRequestId) {
        return post(BASE_PATH + "/actions/" + accessRequestId + "/execute", Map.of());
    }

    public JsonNode listCountryOperations() {
        return get(BASE_PATH + "/country-operations");
    }

    public JsonNode getCountryOperation(UUID id) {
        return get(BASE_PATH + "/country-operations/" + id);
    }

    public JsonNode listAppointments(UUID countryOperationId) {
        return get(BASE_PATH + "/country-operations/" + countryOperationId + "/appointments");
    }

    /**
     * Pending platform-action approvals inbox. Proxies the existing governance
     * access-request read and filters to PLATFORM_ACTION requests in the given
     * status (default PENDING), so the console is not limited to in-session ids.
     */
    public JsonNode listPendingActions(String status) {
        String wanted = status == null || status.isBlank() ? "PENDING" : status;
        JsonNode all = get("/v1/internal/governance/access-requests");
        ArrayNode filtered = JsonNodeFactory.instance.arrayNode();
        if (all != null && all.isArray()) {
            for (JsonNode req : all) {
                String type = req.path("requestType").asText("");
                String st = req.path("status").asText("");
                if ("PLATFORM_ACTION".equalsIgnoreCase(type) && wanted.equalsIgnoreCase(st)) {
                    filtered.add(req);
                }
            }
        }
        return filtered;
    }

    /** Who-approved projection for a platform action (read-only). */
    public JsonNode getActionApprovals(UUID accessRequestId) {
        return get(BASE_PATH + "/actions/" + accessRequestId + "/approvals");
    }

    public JsonNode initiateAppointment(UUID countryOperationId, Map<String, Object> body) {
        return post(BASE_PATH + "/country-operations/" + countryOperationId + "/appointments", body);
    }

    public JsonNode initiateRevocation(UUID countryOperationId, UUID appointmentId, Map<String, Object> body) {
        String url = trimSlash(baseUrl) + BASE_PATH + "/country-operations/" + countryOperationId
                + "/appointments/" + appointmentId;
        log.info("Platform origin: DELETE {}", url);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body != null ? body : Map.of(), jsonHeaders());
        return extractData(restTemplate.exchange(url, HttpMethod.DELETE, entity, JsonNode.class));
    }

    private JsonNode get(String relativePath) {
        String url = trimSlash(baseUrl) + relativePath;
        log.info("Platform origin: GET {}", relativePath);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    private JsonNode post(String relativePath, Map<String, Object> body) {
        String url = trimSlash(baseUrl) + relativePath;
        log.info("Platform origin: POST {}", relativePath);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body != null ? body : Map.of(), jsonHeaders());
        return extractData(restTemplate.postForEntity(url, entity, JsonNode.class));
    }

    /** Fail closed: non-2xx or empty upstream body raises, so the BFF controller returns 502. */
    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Platform origin governance upstream returned "
                    + response.getStatusCode());
        }
        JsonNode body = response.getBody();
        return body.has("data") ? body.get("data") : body;
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static String trimSlash(String base) {
        if (base == null) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
