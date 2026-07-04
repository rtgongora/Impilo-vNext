package zw.gov.mohcc.impilo.live.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client for document-service (the artifact system of record, port 8093).
 *
 * Used by the replay pipeline to adopt rtc-gateway recording egress objects into
 * the document catalogue (register-external) and to mint time-limited signed
 * playback URLs for catalogued replay artifacts.
 *
 * Calls may run on Kafka consumer threads where no request TrustContext exists,
 * so the tenant is passed explicitly and the client stamps its own system actor
 * headers. Failures return an empty map (estate integration convention) — the
 * caller decides whether that leaves state untouched for a later retry.
 */
@Component
public class DocumentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceClient.class);

    static final String SYSTEM_ACTOR = "live-service";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DocumentServiceClient(RestTemplate restTemplate,
                                 @Value("${live.integrations.document-service-base-url:http://localhost:8093}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = IntegrationHttpSupport.trimSlash(baseUrl);
    }

    /**
     * Adopt an externally written MinIO object into the document catalogue.
     * POST /v1/internal/objects/register-external
     */
    public Map<String, Object> registerExternal(UUID tenantId, Map<String, Object> request) {
        return exchange(tenantId, HttpMethod.POST, baseUrl + "/v1/internal/objects/register-external", request);
    }

    /**
     * Time-limited playback URL for a catalogued object.
     * GET /v1/internal/objects/{objectId}/signed-url
     */
    public Map<String, Object> getSignedUrl(UUID tenantId, String objectId) {
        return exchange(tenantId, HttpMethod.GET,
                baseUrl + "/v1/internal/objects/" + objectId + "/signed-url", null);
    }

    private Map<String, Object> exchange(UUID tenantId, HttpMethod method, String url, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            if (tenantId != null) {
                headers.set(TrustContext.H_TENANT_ID, tenantId.toString());
            }
            headers.set(TrustContext.H_ACTOR_ID, SYSTEM_ACTOR);
            headers.set(TrustContext.H_ACTOR_TYPE, "SERVICE");
            headers.set(TrustContext.H_CORRELATION_ID, UUID.randomUUID().toString());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, method,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception ex) {
            log.warn("DocumentServiceClient {} {} failed: {}", method, url, ex.getMessage());
            return Map.of();
        }
    }
}
