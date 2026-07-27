package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * HTTP client for Workforce Governance (organisations, assignments, scope).
 */
@Component
public class WorkforceGovernanceClient {

    private static final Logger log = LoggerFactory.getLogger(WorkforceGovernanceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public WorkforceGovernanceClient(RestTemplate serviceRestTemplate,
                                     ServiceClientConfig.ServiceEndpoints endpoints,
                                     ObjectMapper objectMapper) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.workforceGovernanceBaseUrl();
        this.objectMapper = objectMapper;
    }

    public JsonNode listOrganisations() {
        String url = trimSlash(baseUrl) + "/v1/internal/governance/organisations";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.warn("Governance list organisations failed: {}", response.getStatusCode());
            return null;
        }
        return response.getBody().path("data");
    }

    // ── Regulator bootstrap (RB-3b) — the founding-administrator four-eyes rail ───────────
    // The BFF gates these on person assurance (LOA3) before proxying; wgv owns the request,
    // the two-person approval and the activation event.

    private static final String BOOTSTRAP_BASE =
            "/internal/v1/workforce-governance/regulator-bootstrap";

    public JsonNode submitBootstrapRequest(Object body) {
        return unwrap(restTemplate.postForEntity(
                trimSlash(baseUrl) + BOOTSTRAP_BASE + "/requests", body, JsonNode.class));
    }

    public JsonNode approveBootstrap(String accessRequestId, Object body) {
        return unwrap(restTemplate.postForEntity(
                trimSlash(baseUrl) + BOOTSTRAP_BASE + "/actions/" + accessRequestId + "/approve",
                body, JsonNode.class));
    }

    public JsonNode activateBootstrap(String accessRequestId) {
        return unwrap(restTemplate.postForEntity(
                trimSlash(baseUrl) + BOOTSTRAP_BASE + "/actions/" + accessRequestId + "/activate",
                null, JsonNode.class));
    }

    public JsonNode listBootstrapRequests(String organisationId, String status) {
        StringBuilder url = new StringBuilder(trimSlash(baseUrl) + BOOTSTRAP_BASE + "/requests");
        java.util.List<String> q = new java.util.ArrayList<>();
        if (organisationId != null && !organisationId.isBlank()) {
            q.add("organisationId=" + organisationId);
        }
        if (status != null && !status.isBlank()) {
            q.add("status=" + status);
        }
        if (!q.isEmpty()) {
            url.append("?").append(String.join("&", q));
        }
        return unwrap(restTemplate.getForEntity(url.toString(), JsonNode.class));
    }

    private JsonNode unwrap(ResponseEntity<JsonNode> response) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.warn("Governance bootstrap call failed: {}", response.getStatusCode());
            return null;
        }
        return response.getBody().path("data");
    }

    public JsonNode organisationSummary(String organisationId) {
        String url = trimSlash(baseUrl) + "/v1/internal/governance/summaries/organisation/" + organisationId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }
        return response.getBody().path("data");
    }

    public JsonNode facilityGovernanceSummary(long facilityId) {
        String url = trimSlash(baseUrl) + "/v1/internal/governance/summaries/facility/" + facilityId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }
        return response.getBody().path("data");
    }

    public JsonNode searchAssignments(String subjectType, String subjectId, String status) {
        String url = trimSlash(baseUrl) + "/v1/internal/governance/assignments/search"
                + "?subjectType=" + (subjectType != null ? subjectType : "")
                + "&subjectId=" + (subjectId != null ? subjectId : "")
                + "&status=" + (status != null ? status : "");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }
        return response.getBody().path("data");
    }

    public JsonNode listJurisdictions() {
        String url = trimSlash(baseUrl) + "/v1/internal/governance/jurisdictions";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }
        return response.getBody().path("data");
    }

    /**
     * EC-number employment match (IATG Wave-1, Channel B):
     * POST /internal/v1/workforce-governance/employment/match with
     * {@code {ecNumber, healthId?, nationalId?, surname?}} →
     * {@code {matchStatus: MATCHED_BOTH|MATCHED_EMPLOYMENT_ONLY|NOT_FOUND|CONFLICT, ...}}.
     *
     * <p>Trust headers are forwarded by the shared interceptor; a fresh
     * {@code Idempotency-Key} is minted per call because the match is a
     * read-shaped command — replaying a stale stored result would be dishonest.
     * Returns {@code null} when governance is unavailable.</p>
     */
    public JsonNode matchEmployment(Object request) {
        try {
            String url = trimSlash(baseUrl) + "/internal/v1/workforce-governance/employment/match";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Idempotency-Key", java.util.UUID.randomUUID().toString());
            String json = request instanceof String s ? s : objectMapper.writeValueAsString(request);
            ResponseEntity<JsonNode> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(json, headers), JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Governance employment match failed: {}", response.getStatusCode());
                return null;
            }
            return response.getBody().path("data");
        } catch (Exception e) {
            log.warn("Governance employment match error: {}", e.getMessage());
            return null;
        }
    }

    public JsonNode postJson(String relativePath, Object body) {
        try {
            String url = trimSlash(baseUrl) + relativePath;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String json = body instanceof String s ? s : objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Governance POST {} failed: {}", relativePath, response.getStatusCode());
                return null;
            }
            return response.getBody().path("data");
        } catch (Exception e) {
            log.warn("Governance POST {} error: {}", relativePath, e.getMessage());
            return null;
        }
    }

    public JsonNode getJson(String relativePath) {
        try {
            String url = trimSlash(baseUrl) + relativePath;
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Governance GET {} failed: {}", relativePath, response.getStatusCode());
                return null;
            }
            return response.getBody().path("data");
        } catch (Exception e) {
            log.warn("Governance GET {} error: {}", relativePath, e.getMessage());
            return null;
        }
    }

    public JsonNode patchJson(String relativePath, Object body) {
        try {
            String url = trimSlash(baseUrl) + relativePath;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String json = body instanceof String s ? s : objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.PATCH, entity, JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Governance PATCH {} failed: {}", relativePath, response.getStatusCode());
                return null;
            }
            return response.getBody().path("data");
        } catch (Exception e) {
            log.warn("Governance PATCH {} error: {}", relativePath, e.getMessage());
            return null;
        }
    }

    private static String trimSlash(String base) {
        if (base == null) {
            return "";
        }
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
}
