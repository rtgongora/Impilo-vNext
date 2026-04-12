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
 * HTTP client for the TSHEPO Authorization sovereign service.
 *
 * <p>Provides access to policy rules, break-glass review workflows, and device
 * trust management. TSHEPO Authz is the legitimacy engine that enforces the
 * Health OS trust model — every request flows through ext_authz before reaching
 * any downstream service.</p>
 *
 * <p>Trust headers are automatically forwarded by the shared
 * {@link ServiceClientConfig} interceptor.</p>
 */
@Component
public class TshepoAuthzServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TshepoAuthzServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TshepoAuthzServiceClient(RestTemplate serviceRestTemplate,
                                     ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.tshepoAuthzBaseUrl();
    }

    /**
     * List all policy rules configured in TSHEPO.
     */
    public JsonNode listPolicyRules() {
        String url = baseUrl + "/v1/policies";
        log.info("TSHEPO-AUTHZ: Listing policy rules");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Create a new policy rule.
     */
    public JsonNode createPolicyRule(Map<String, Object> request) {
        String url = baseUrl + "/v1/policies";
        log.info("TSHEPO-AUTHZ: Creating policy rule");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get pending break-glass access reviews.
     */
    public JsonNode getPendingBreakGlassReviews() {
        String url = baseUrl + "/v1/break-glass/review";
        log.info("TSHEPO-AUTHZ: Getting pending break-glass reviews");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Review (approve/deny) a break-glass access request.
     */
    public JsonNode reviewBreakGlass(String id, Map<String, Object> request) {
        String url = baseUrl + "/v1/break-glass/review/" + id;
        log.info("TSHEPO-AUTHZ: Reviewing break-glass id={}", id);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get device trust profile by fingerprint.
     */
    public JsonNode getDeviceProfile(String fingerprint) {
        String url = baseUrl + "/v1/devices/" + fingerprint;
        log.info("TSHEPO-AUTHZ: Getting device profile fingerprint={}", fingerprint);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Block a device by fingerprint.
     */
    public JsonNode blockDevice(String fingerprint) {
        String url = baseUrl + "/v1/devices/" + fingerprint + "/block";
        log.info("TSHEPO-AUTHZ: Blocking device fingerprint={}", fingerprint);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /**
     * Unblock a device by fingerprint (removes the block).
     */
    public void unblockDevice(String fingerprint) {
        String url = baseUrl + "/v1/devices/" + fingerprint + "/block";
        log.info("TSHEPO-AUTHZ: Unblocking device fingerprint={}", fingerprint);
        restTemplate.delete(url);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
