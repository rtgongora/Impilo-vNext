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
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the Data Governance sovereign service (datasets, grants, policies, rules, snapshots).
 */
@Component
public class DataGovernanceServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DataGovernanceServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DataGovernanceServiceClient(RestTemplate serviceRestTemplate,
                                       ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.dataGovernanceBaseUrl();
    }

    public JsonNode listDatasetsInternal() {
        String url = baseUrl + "/internal/v1/governance/datasets";
        log.info("Data governance: list datasets (internal)");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createDataset(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/governance/datasets";
        log.info("Data governance: create dataset [name={}]", request.get("name"));
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode createGrant(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/governance/grants";
        log.info("Data governance: create grant");
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode revokeGrant(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/governance/grants/revoke";
        log.info("Data governance: revoke grant");
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode publishPolicy(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/governance/policies";
        log.info("Data governance: publish policy [name={}]", request.get("name"));
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode decide(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/governance/decide";
        log.info("Data governance: decide [dataset={}, purpose={}]",
                request.get("dataset"), request.get("purposeOfUse"));
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode listDatasetsExternal() {
        String url = baseUrl + "/external/v1/governance/datasets";
        log.info("Data governance: list datasets (external)");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createRule(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/governance/rules";
        log.info("Data governance: create rule [name={}]", request.get("name"));
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode listRules() {
        String url = baseUrl + "/internal/v1/governance/rules";
        log.info("Data governance: list rules");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getRule(String ruleId) {
        String url = baseUrl + "/internal/v1/governance/rules/" + ruleId;
        log.info("Data governance: get rule [id={}]", ruleId);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listDatasetSnapshots() {
        String url = baseUrl + "/internal/v1/snapshots/datasets";
        log.info("Data governance: snapshots datasets");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listPolicySnapshots() {
        String url = baseUrl + "/internal/v1/snapshots/policies";
        log.info("Data governance: snapshots policies");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createExport(Map<String, Object> request) {
        String url = baseUrl + "/external/v1/exports";
        log.info("Data governance: export request");
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    // ── Data-subject rights (account deletion / erasure) — Privacy-by-Architecture §6 ──

    /** Submit an account-deletion / erasure request. Returns the persisted request body. */
    public JsonNode submitDataSubjectRequest(Map<String, Object> request, String idempotencyKey) {
        String url = baseUrl + "/internal/v1/governance/data-subject-requests";
        log.info("Data governance: submit data-subject request [subject={}]", request.get("subjectId"));
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, withIdempotencyKey(request, idempotencyKey), JsonNode.class);
        return response.getBody();
    }

    /** Latest data-subject request for the subject, or {@code null} when none exists (404). */
    public JsonNode getDataSubjectRequestStatus(String subjectId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/internal/v1/governance/data-subject-requests")
                .queryParam("subjectId", subjectId)
                .toUriString();
        log.info("Data governance: data-subject request status [subject={}]", subjectId);
        try {
            return restTemplate.getForEntity(url, JsonNode.class).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    /** Cancel a pending request. Returns the cancelled body, or {@code null} when nothing is pending (404). */
    public JsonNode cancelDataSubjectRequest(Map<String, Object> request, String idempotencyKey) {
        String url = baseUrl + "/internal/v1/governance/data-subject-requests/cancel";
        log.info("Data governance: cancel data-subject request [subject={}]", request.get("subjectId"));
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.POST, withIdempotencyKey(request, idempotencyKey), JsonNode.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    /**
     * Wrap the body with a fallback Idempotency-Key so writes to {@code /internal/v1/**}
     * always satisfy the command-idempotency filter. The trust-header interceptor overrides
     * this with the inbound key when the caller supplied one (it only sets present headers).
     */
    private static HttpEntity<Map<String, Object>> withIdempotencyKey(Map<String, Object> body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CompanionHeaders.IDEMPOTENCY_KEY,
                idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : UUID.randomUUID().toString());
        return new HttpEntity<>(body, headers);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
