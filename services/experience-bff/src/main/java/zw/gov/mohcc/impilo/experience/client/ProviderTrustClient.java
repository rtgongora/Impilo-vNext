package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for the VARAPI provider trust API (IATG Wave-1 contract):
 * {@code /v1/internal/providers/{providerId}/trust} where {@code providerId}
 * is the numeric id or the providerPublicId.
 *
 * <p>Deliberately separate from {@link VarapiServiceClient} (owned by another
 * workstream) but resolves the base URL through the same
 * {@link ServiceClientConfig.ServiceEndpoints#varapiBaseUrl()} so both clients
 * always target the same registry instance. Trust headers are forwarded by the
 * shared {@code serviceRestTemplate} interceptor.</p>
 *
 * <p>Trust levels move forward only (SELF_ASSERTED → … → FULLY_LINKED_OR_ONBOARDED);
 * VARAPI answers 409 for a downgrade unless evidence.type=EVIDENCE_REVOKED. This
 * client never throws for HTTP errors — callers receive an explicit
 * {@link TrustCallResult} and degrade honestly.</p>
 */
@Component
public class ProviderTrustClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderTrustClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public ProviderTrustClient(RestTemplate serviceRestTemplate,
                               ServiceClientConfig.ServiceEndpoints endpoints,
                               ObjectMapper objectMapper) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.varapiBaseUrl();
        this.objectMapper = objectMapper;
    }

    /**
     * Outcome of a trust API call. {@code statusCode} is 0 when the upstream
     * was unreachable (network/timeout) rather than answering an HTTP error.
     */
    public record TrustCallResult(boolean success, int statusCode, JsonNode body) {

        public static TrustCallResult ok(int statusCode, JsonNode body) {
            return new TrustCallResult(true, statusCode, body);
        }

        public static TrustCallResult failed(int statusCode, JsonNode body) {
            return new TrustCallResult(false, statusCode, body);
        }
    }

    /**
     * POST /v1/internal/providers/{providerId}/trust — assert a trust level
     * with evidence, e.g. {@code {"targetLevel":"EMPLOYMENT_MATCHED",
     * "evidence":{"type":"EC_MATCH","source":"HSC_EMPLOYMENT","ref":"EC***",
     * "confidence":0.95},"channel":"WORKFORCE_B"}}.
     */
    public TrustCallResult assertTrust(String providerId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/providers/" + providerId + "/trust";
        log.info("VARAPI-TRUST: asserting trust for provider={} targetLevel={}",
                providerId, body != null ? body.get("targetLevel") : null);
        try {
            ResponseEntity<JsonNode> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
            return TrustCallResult.ok(response.getStatusCode().value(), extractData(response));
        } catch (HttpStatusCodeException e) {
            log.warn("VARAPI-TRUST: assert for provider={} rejected: {}", providerId, e.getStatusCode());
            return TrustCallResult.failed(e.getStatusCode().value(), parseBody(e));
        } catch (Exception e) {
            log.warn("VARAPI-TRUST: assert for provider={} unavailable: {}", providerId, e.getMessage());
            return TrustCallResult.failed(0, null);
        }
    }

    /**
     * GET /v1/internal/providers/{providerId}/trust — current trust level,
     * registry status, onboarding channel, and evidence attempts.
     * Returns {@code null} when the upstream fails; callers degrade honestly.
     */
    public JsonNode getTrust(String providerId) {
        String url = baseUrl + "/v1/internal/providers/" + providerId + "/trust";
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            return extractData(response);
        } catch (Exception e) {
            log.warn("VARAPI-TRUST: trust read for provider={} unavailable: {}", providerId, e.getMessage());
            return null;
        }
    }

    private static JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }

    private JsonNode parseBody(HttpStatusCodeException e) {
        try {
            String body = e.getResponseBodyAsString();
            return body == null || body.isBlank() ? null : objectMapper.readTree(body);
        } catch (Exception ignored) {
            return null;
        }
    }
}
