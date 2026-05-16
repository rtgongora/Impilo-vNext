package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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

    /**
     * Synthetic ext_authz check for Health Intelligence plane queries.
     * Uses {@code :path} {@code /internal/v1/intelligence-plane} so {@code PolicyEngine}
     * derives resource type {@code intelligence-plane} (see tshepo-authz V006 migration).
     */
    public boolean intelligencePlaneQueryAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/intelligence-plane");
    }

    /**
     * Synthetic ext_authz check for Experience registry intake mutations.
     * Uses {@code :method} / {@code :path} headers so {@code PolicyEngine} derives
     * resource type {@code registry-intake} (see tshepo-authz V005 migration).
     */
    public boolean registryIntakeMutationAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/registry-intake");
    }

    /**
     * Synthetic ext_authz for governed imaging reads (search, study detail, series list).
     * Resource type {@code imaging-governed-read} (see PolicyEngine / Tshepo migrations).
     */
    public boolean imagingGovernedReadAllowed() {
        return syntheticAuthorizeVerdict("GET", "/internal/v1/imaging-governed-read");
    }

    /**
     * Synthetic ext_authz for governed imaging mutations (correlate, sync, report/order links).
     */
    public boolean imagingGovernedMutateAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/imaging-governed-mutate");
    }

    /**
     * Synthetic ext_authz for DICOM viewer session creation.
     */
    public boolean imagingViewerLaunchAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/imaging-viewer-launch");
    }

    /**
     * Synthetic ext_authz for telemedicine read operations.
     */
    public boolean telemedicineReadAllowed() {
        return syntheticAuthorizeVerdict("GET", "/internal/v1/telemedicine-governed-read");
    }

    /**
     * Synthetic ext_authz for telemedicine lifecycle mutations.
     */
    public boolean telemedicineMutateAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/telemedicine-governed-mutate");
    }

    /**
     * Synthetic ext_authz for telemedicine break-glass overrides.
     */
    public boolean telemedicineBreakGlassAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/telemedicine-break-glass-override");
    }

    /**
     * Synthetic ext_authz for Experience enterprise-domain finance routes (billing workspace or MusheX platform).
     * Resource type is derived from the last path segment (see tshepo-authz V007).
     */
    public boolean financePlaneAllowed(String httpMethod, String syntheticPath) {
        return syntheticAuthorizeVerdict(httpMethod, syntheticPath);
    }

    /**
     * Synthetic ext_authz for Experience shell workspace persistence (pins / recents).
     * Tshepo PolicyEngine maps the last non-UUID URL segment of {@code :path} to resource type
     * {@code workspace-state} for {@code /internal/v1/shell/workspace-state}
     * (tshepo-authz migration {@code V008__shell_workspace_policy_rules.sql}).
     */
    public boolean shellWorkspaceStateAllowed(String httpMethod) {
        return syntheticAuthorizeVerdict(httpMethod, "/internal/v1/shell/workspace-state");
    }

    private boolean syntheticAuthorizeVerdict(String method, String path) {
        String url = baseUrl + "/v1/authorize";
        HttpHeaders headers = new HttpHeaders();
        headers.set(":method", method);
        headers.set(":path", path);
        try {
            ResponseEntity<JsonNode> response =
                    restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(headers),
                            JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return false;
            }
            JsonNode body = response.getBody();
            if (!body.has("verdict")) {
                return false;
            }
            return "ALLOW".equalsIgnoreCase(body.get("verdict").asText());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 403 || e.getStatusCode().value() == 401) {
                log.debug("TSHEPO-AUTHZ: synthetic authorize denied path={} status={}", path, e.getStatusCode());
                return false;
            }
            return false;
        } catch (Exception e) {
            log.warn("TSHEPO-AUTHZ: synthetic authorize failed path={}: {}", path, e.getMessage());
            return false;
        }
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
