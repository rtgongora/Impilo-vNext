package zw.gov.mohcc.impilo.mvumo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client to {@code tshepo-consent-service} — materialises FHIR R4 Consent directives on grant, revokes on withdraw.
 */
@Component
public class TshepoConsentClient {

    private static final Logger log = LoggerFactory.getLogger(TshepoConsentClient.class);

    private final RestTemplate tshepoRestTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final boolean enabled;
    private final Tracer tracer;

    public TshepoConsentClient(
            @Qualifier("tshepoConsentRestTemplate") RestTemplate tshepoRestTemplate,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${mvumo.tshepo-consent.base-url:http://localhost:8182}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value("${mvumo.tshepo-consent.enabled:true}") boolean enabled,
            ObjectProvider<Tracer> tracer) {
        this.tshepoRestTemplate = tshepoRestTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.trim().replaceAll("/$", "");
        this.enabled = enabled;
        this.tracer = tracer.getIfAvailable();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * POST /v1/consent — returns Tshepo directive id or throws.
     */
    public UUID createDirective(Map<String, Object> createBody) {
        if (!enabled) {
            log.warn("Tshepo consent integration disabled; skipping createDirective");
            return null;
        }
        if (tracer == null) {
            return doCreateDirect(createBody);
        }
        Span span = tracer.nextSpan().name("tshepo.consent.create").tag("outbound", "tshepo-consent-service");
        try (var ignored = tracer.withSpan(span.start())) {
            return doCreateDirect(createBody);
        } finally {
            span.end();
        }
    }

    private UUID doCreateDirect(Map<String, Object> createBody) {
        String url = baseUrl + "/v1/consent";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(createBody, headers);
            ResponseEntity<String> response = tshepoRestTemplate.postForEntity(url, entity, String.class);
            return parseDataId(response.getBody());
        } catch (HttpStatusCodeException e) {
            log.error("tshepo-consent create failed: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("tshepo-consent create failed: {}", e.getMessage());
            throw new IllegalStateException("tshepo-consent create failed: " + e.getMessage(), e);
        }
    }

    /**
     * GET /v1/consent/evaluate — delegates live consent enforcement decisioning to tshepo-consent-service.
     */
    public Map<String, Object> evaluateDirective(
            UUID tenantId, String actorId, String subjectRef, String purpose, String scope) {
        if (!enabled) {
            throw new IllegalStateException("tshepo-consent integration is disabled");
        }
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/v1/consent/evaluate")
                .queryParam("tenantId", tenantId)
                .queryParam("actorId", actorId)
                .queryParam("subjectRef", subjectRef)
                .queryParam("purpose", purpose)
                .queryParam("scope", scope)
                .build()
                .encode()
                .toUriString();
        try {
            ResponseEntity<String> response = tshepoRestTemplate.getForEntity(url, String.class);
            return parseDataMap(response.getBody());
        } catch (HttpStatusCodeException e) {
            log.error("tshepo-consent evaluate failed: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("tshepo-consent evaluate failed: {}", e.getMessage());
            throw new IllegalStateException("tshepo-consent evaluate failed: " + e.getMessage(), e);
        }
    }

    /**
     * GET /v1/consent/{id} — returns the directive's status (e.g. ACTIVE/REVOKED/SUSPENDED), or
     * {@code null} if the directive is not found (404). Used by the reconciliation job to detect
     * out-of-band drift between a Mvumo grant and the trust-layer directive.
     */
    public String getDirectiveStatus(UUID tshepoConsentId) {
        if (!enabled) {
            return null;
        }
        String url = baseUrl + "/v1/consent/" + tshepoConsentId;
        try {
            ResponseEntity<String> response = tshepoRestTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.get("data");
            if (data == null || data.isNull() || !data.has("status")) {
                return null;
            }
            return data.get("status").asText(null);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                return null; // directive gone → treat as drift by the caller
            }
            log.error("tshepo-consent get-status failed: id={} status={}", tshepoConsentId, e.getStatusCode());
            throw e;
        } catch (Exception e) {
            log.error("tshepo-consent get-status failed: id={} err={}", tshepoConsentId, e.getMessage());
            throw new IllegalStateException("tshepo-consent get-status failed: " + e.getMessage(), e);
        }
    }

    public void revokeDirective(UUID tshepoConsentId, String reason) {
        if (!enabled) {
            log.warn("Tshepo consent integration disabled; skipping revokeDirective");
            return;
        }
        if (tracer == null) {
            doRevokeDirect(tshepoConsentId, reason);
            return;
        }
        Span span = tracer.nextSpan().name("tshepo.consent.revoke").tag("outbound", "tshepo-consent-service");
        try (var ignored = tracer.withSpan(span.start())) {
            doRevokeDirect(tshepoConsentId, reason);
        } finally {
            span.end();
        }
    }

    private void doRevokeDirect(UUID tshepoConsentId, String reason) {
        String url = baseUrl + "/v1/consent/" + tshepoConsentId;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of("reason", reason != null && !reason.isBlank() ? reason : "Revoked from Mvumo");
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            tshepoRestTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
        } catch (HttpStatusCodeException e) {
            log.error("tshepo-consent revoke failed: id={} status={} body={}", tshepoConsentId, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    private UUID parseDataId(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("empty response from tshepo-consent");
        }
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.get("data");
        if (data == null || !data.has("id")) {
            throw new IllegalStateException("unexpected response shape from tshepo-consent");
        }
        return UUID.fromString(data.get("id").asText());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDataMap(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("empty response from tshepo-consent");
        }
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.get("data");
        if (data == null || data.isNull() || !data.isObject()) {
            throw new IllegalStateException("unexpected response shape from tshepo-consent");
        }
        return objectMapper.convertValue(data, Map.class);
    }
}
