package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the TSHEPO Audit sovereign service.
 *
 * <p>Provides access to the immutable audit event log, patient access history,
 * and SHA-256 hash chain integrity verification. The audit service captures
 * every access decision made by TSHEPO Authz, forming an unbroken chain of
 * custody for all health data access.</p>
 *
 * <p>Trust headers are automatically forwarded by the shared
 * {@link ServiceClientConfig} interceptor.</p>
 */
@Component
public class TshepoAuditServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TshepoAuditServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TshepoAuditServiceClient(RestTemplate serviceRestTemplate,
                                     ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.tshepoAuditBaseUrl();
    }

    /**
     * Query audit events with optional filters (actorId, subjectRef, eventType, time range).
     */
    public JsonNode queryEvents(String actorId, String subjectRef, String eventType,
                                String fromTime, String toTime, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/audit/events");
        if (actorId != null) builder.queryParam("actorId", actorId);
        if (subjectRef != null) builder.queryParam("subjectRef", subjectRef);
        if (eventType != null) builder.queryParam("eventType", eventType);
        if (fromTime != null) builder.queryParam("fromTime", fromTime);
        if (toTime != null) builder.queryParam("toTime", toTime);
        builder.queryParam("page", page);
        builder.queryParam("size", size);
        String url = builder.toUriString();
        log.info("TSHEPO-AUDIT: Querying events");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a single audit event by ID.
     */
    public JsonNode getEvent(UUID id) {
        String url = baseUrl + "/v1/audit/events/" + id;
        log.info("TSHEPO-AUDIT: Getting event id={}", id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get patient access history (redacted view for patient portal).
     */
    public JsonNode getAccessHistory(String subjectRef, int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/audit/access-history/" + subjectRef)
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        log.info("TSHEPO-AUDIT: Getting access history for subject={}", subjectRef);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Verify SHA-256 hash chain integrity for a tenant.
     */
    public JsonNode verifyChainIntegrity(Map<String, Object> request) {
        String url = baseUrl + "/v1/audit/verify-chain";
        log.info("TSHEPO-AUDIT: Verifying chain integrity");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /**
     * Append an immutable audit event (POST /v1/audit/events). Failures are logged and swallowed
     * so clinical flows are not blocked when the audit plane is unavailable.
     */
    public void ingestAuditEvent(Map<String, Object> request) {
        String url = baseUrl + "/v1/audit/events";
        try {
            restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        } catch (Exception e) {
            log.warn("TSHEPO-AUDIT: ingest failed: {}", e.getMessage());
        }
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
