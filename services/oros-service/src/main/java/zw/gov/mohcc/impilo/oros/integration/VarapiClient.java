package zw.gov.mohcc.impilo.oros.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.Optional;

/**
 * Read-only client for the VARAPI provider registry.
 *
 * <p>Resolves the human-readable name of a referring/ordering provider given its VARAPI public
 * ID, used to stamp {@code referring_provider_name} on diagnostic orders at submit time.</p>
 *
 * <p><b>Honest degradation:</b> if VARAPI is not configured ({@code base-url} blank) or is
 * unreachable, lookups return {@link Optional#empty()} and the caller keeps any
 * client-supplied name — provider resolution never blocks the order lifecycle. The
 * {@link #isConfigured()} flag reflects the configured/not-configured state explicitly.</p>
 */
@Service
public class VarapiClient {

    private static final Logger log = LoggerFactory.getLogger(VarapiClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public VarapiClient(RestTemplate restTemplate,
                        @Value("${oros.integration.varapi.base-url:http://localhost:8083}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    /** Whether a VARAPI endpoint is configured (base URL present). */
    public boolean isConfigured() {
        return !baseUrl.isBlank();
    }

    /**
     * Look up the display name for a provider by VARAPI public ID.
     *
     * @return the resolved name, or empty if not configured / unreachable / not found
     */
    @SuppressWarnings("unchecked")
    public Optional<String> lookupProviderName(String providerId) {
        if (!isConfigured() || providerId == null || providerId.isBlank()) {
            return Optional.empty();
        }
        try {
            String url = baseUrl + "/v1/internal/providers/" + providerId;
            HttpEntity<Void> request = new HttpEntity<>(buildTrustHeaders());
            ResponseEntity<Map> response =
                    restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return extractName(response.getBody());
            }
            log.warn("VARAPI returned non-success status {} for provider {}",
                    response.getStatusCode(), providerId);
            return Optional.empty();

        } catch (RestClientException e) {
            log.warn("VARAPI unavailable for provider lookup {}: {}", providerId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolve a provider name from a response body, tolerating either a bare provider object or
     * an {@code ApiResponse}-style envelope ({@code {data: {...}}}).
     */
    @SuppressWarnings("unchecked")
    private Optional<String> extractName(Map<String, Object> body) {
        Object data = body.get("data");
        Map<String, Object> provider = (data instanceof Map) ? (Map<String, Object>) data : body;

        for (String key : new String[]{"fullName", "displayName", "name", "providerName"}) {
            Object v = provider.get(key);
            if (v instanceof String s && !s.isBlank()) {
                return Optional.of(s);
            }
        }
        // Compose from given/family parts if present.
        String given = asString(provider.get("givenName"));
        String family = asString(provider.get("familyName"));
        String composed = (given + " " + family).trim();
        return composed.isBlank() ? Optional.empty() : Optional.of(composed);
    }

    private String asString(Object v) {
        return v instanceof String s ? s : "";
    }

    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.facilityId() != null) {
                headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            }
        } catch (IllegalStateException e) {
            log.debug("No trust context available for VARAPI call headers");
        }
        return headers;
    }
}
