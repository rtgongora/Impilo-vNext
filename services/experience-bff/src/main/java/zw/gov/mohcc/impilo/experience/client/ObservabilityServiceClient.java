package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.OrchestrationBacklogEndpoints;

import java.net.URI;
import java.util.UUID;

/**
 * HTTP client for the observability-service (platform heartbeat / ops telemetry).
 *
 * <p>Two lanes only, both driven by the public gateway BFF:</p>
 * <ul>
 *   <li>{@link #publicServiceStatus()} — GET the PII-free service-heartbeat status feed
 *       that the citizen service-status board projects into coarse group states.</li>
 *   <li>{@link #postClientEvents(JsonNode)} — POST a sanitized client-telemetry batch.
 *       Best-effort: telemetry must never break a request, so transport failures are
 *       logged and swallowed.</li>
 * </ul>
 *
 * <p>The base URL is bound from {@code OBSERVABILITY_BASE_URL}
 * ({@link OrchestrationBacklogEndpoints#getObservabilityBaseUrl()}). Requests use the
 * shared trust-forwarding {@code serviceRestTemplate}; because the public status read
 * originates from an anonymous lane (trust headers stripped at the edge) it supplies
 * service-originated default headers, mirroring the other public-lane clients.</p>
 */
@Component
public class ObservabilityServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityServiceClient.class);

    private static final String PUBLIC_DEFAULT_TENANT = zw.gov.mohcc.impilo.experience.config.PublicTenants.REGISTRY_PLANE; // registry plane — see PublicTenants

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ObservabilityServiceClient(RestTemplate serviceRestTemplate,
                                      OrchestrationBacklogEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.getObservabilityBaseUrl();
    }

    /**
     * PII-free public service-heartbeat status feed:
     * {@code { generatedAt, staleThresholdMinutes, services:[{serviceName,status,lastHeartbeat}] }}.
     * Throws on transport failure so the caller can fall back to {@code live:false}.
     */
    public JsonNode publicServiceStatus() {
        String url = baseUrl + "/v1/public/ops/status";
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                URI.create(url), HttpMethod.GET, anonymousSafeEntity(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Forward a sanitized client-telemetry batch ({@code { events:[...] }}) to the ops
     * ingest endpoint. Best-effort by contract: any failure is logged and swallowed so a
     * telemetry hiccup can never surface as a client error (which would trigger retry storms).
     */
    public void postClientEvents(JsonNode batch) {
        String url = baseUrl + "/internal/v1/ops/client-events";
        try {
            HttpHeaders headers = serviceHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.exchange(URI.create(url), HttpMethod.POST,
                    new HttpEntity<>(batch, headers), JsonNode.class);
        } catch (Exception e) {
            // Telemetry is fire-and-forget; never propagate.
            log.debug("Observability client-events POST failed (swallowed): {}", e.getMessage());
        }
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }

    /**
     * Anonymous public-lane reads arrive with actor/trust headers stripped at the edge, so the
     * BFF supplies service-originated defaults; the shared forwarding interceptor overwrites
     * any of these with inbound values when the caller actually sent them.
     */
    private HttpEntity<Void> anonymousSafeEntity() {
        return new HttpEntity<>(serviceHeaders());
    }

    private HttpHeaders serviceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", PUBLIC_DEFAULT_TENANT);
        headers.set("X-Pod-ID", "public-gateway");
        headers.set("X-Actor-ID", "public-gateway");
        headers.set("X-Actor-Type", "SYSTEM");
        headers.set("X-Purpose-Of-Use", "PUBLIC_ACCESS");
        headers.set("X-Correlation-ID", UUID.randomUUID().toString());
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        return headers;
    }
}
