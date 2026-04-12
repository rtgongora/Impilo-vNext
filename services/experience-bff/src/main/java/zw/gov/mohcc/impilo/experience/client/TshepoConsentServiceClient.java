package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the TSHEPO Consent sovereign service.
 *
 * <p>Provides access to consent directive CRUD, consent evaluation, and
 * patient-scoped consent listing. The consent service stores FHIR R4-based
 * consent directives and provides the enforcement hook that TSHEPO Authz
 * calls on every clinical access decision.</p>
 *
 * <p>Trust headers are automatically forwarded by the shared
 * {@link ServiceClientConfig} interceptor.</p>
 */
@Component
public class TshepoConsentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TshepoConsentServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TshepoConsentServiceClient(RestTemplate serviceRestTemplate,
                                       ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.tshepoConsentBaseUrl();
    }

    /**
     * List consent directives for a patient, optionally filtered by status.
     */
    public JsonNode listConsents(String subjectId, String status, int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/consent/patient/" + subjectId)
                .queryParam("status", status != null ? status : "ACTIVE")
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        log.info("TSHEPO-CONSENT: Listing consents for subject={}", subjectId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Create a new consent directive.
     */
    public JsonNode createConsent(Map<String, Object> request) {
        String url = baseUrl + "/v1/consent";
        log.info("TSHEPO-CONSENT: Creating consent directive");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /**
     * Evaluate consent for a given access request.
     */
    public JsonNode evaluateConsent(Map<String, Object> request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/consent/evaluate");
        if (request.containsKey("tenantId")) builder.queryParam("tenantId", request.get("tenantId"));
        if (request.containsKey("actorId")) builder.queryParam("actorId", request.get("actorId"));
        if (request.containsKey("subjectRef")) builder.queryParam("subjectRef", request.get("subjectRef"));
        if (request.containsKey("purpose")) builder.queryParam("purpose", request.get("purpose"));
        if (request.containsKey("scope")) builder.queryParam("scope", request.get("scope"));
        String url = builder.toUriString();
        log.info("TSHEPO-CONSENT: Evaluating consent");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Revoke a consent directive (soft delete).
     */
    public JsonNode revokeConsent(UUID id, Map<String, Object> request) {
        String url = baseUrl + "/v1/consent/" + id;
        log.info("TSHEPO-CONSENT: Revoking consent id={}", id);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.DELETE, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a single consent directive by ID.
     */
    public JsonNode getConsent(UUID id) {
        String url = baseUrl + "/v1/consent/" + id;
        log.info("TSHEPO-CONSENT: Getting consent id={}", id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Update a consent preference (upsert).
     */
    public JsonNode updateConsentPreference(Map<String, Object> request) {
        String url = baseUrl + "/v1/consent/preference";
        log.info("TSHEPO-CONSENT: Updating consent preference");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
