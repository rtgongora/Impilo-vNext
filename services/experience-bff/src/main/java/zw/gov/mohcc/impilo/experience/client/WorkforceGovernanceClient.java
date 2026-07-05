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
