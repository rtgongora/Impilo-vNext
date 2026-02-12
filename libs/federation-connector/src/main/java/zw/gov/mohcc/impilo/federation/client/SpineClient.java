package zw.gov.mohcc.impilo.federation.client;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

import java.util.UUID;

/**
 * Client utility to call national spine endpoints.
 *
 * Automatically propagates mandatory v1.1 headers and correlation context
 * from the current request to the spine call.
 *
 * Usage:
 * <pre>
 *   SpineClient spine = new SpineClient(restTemplate, "https://spine.impilo.health");
 *   ResponseEntity&lt;String&gt; result = spine.get("/internal/v1/patients/{cpid}", cpid);
 * </pre>
 */
public class SpineClient {

    private final RestTemplate restTemplate;
    private final String spineBaseUrl;

    public SpineClient(RestTemplate restTemplate, String spineBaseUrl) {
        this.restTemplate = restTemplate;
        this.spineBaseUrl = spineBaseUrl.endsWith("/")
                ? spineBaseUrl.substring(0, spineBaseUrl.length() - 1)
                : spineBaseUrl;
    }

    /**
     * GET request to the national spine with correlation propagation.
     */
    public <T> ResponseEntity<T> get(String path, Class<T> responseType, Object... uriVars) {
        HttpHeaders headers = buildHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(
                spineBaseUrl + path, HttpMethod.GET, entity, responseType, uriVars);
    }

    /**
     * POST request to the national spine with correlation propagation.
     */
    public <T> ResponseEntity<T> post(String path, Object body, Class<T> responseType,
                                       Object... uriVars) {
        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(
                spineBaseUrl + path, HttpMethod.POST, entity, responseType, uriVars);
    }

    /**
     * PUT request to the national spine with correlation propagation.
     */
    public <T> ResponseEntity<T> put(String path, Object body, Class<T> responseType,
                                      Object... uriVars) {
        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(
                spineBaseUrl + path, HttpMethod.PUT, entity, responseType, uriVars);
    }

    /**
     * Build headers with mandatory v1.1 context propagation.
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        RequestContext ctx = RequestContextHolder.get();

        if (ctx != null) {
            headers.set(CompanionHeaders.TENANT_ID, ctx.tenantId());
            headers.set(CompanionHeaders.POD_ID, ctx.podId());
            headers.set(CompanionHeaders.REQUEST_ID, UUID.randomUUID().toString());
            headers.set(CompanionHeaders.CORRELATION_ID, ctx.correlationId());
            if (ctx.authToken() != null) {
                headers.set(CompanionHeaders.AUTHORIZATION,
                        CompanionHeaders.BEARER_PREFIX + ctx.authToken());
            }
            if (ctx.clientTimeoutMs() != null) {
                headers.set(CompanionHeaders.CLIENT_TIMEOUT_MS,
                        String.valueOf(ctx.clientTimeoutMs()));
            }
        } else {
            // No context — generate minimal required headers
            headers.set(CompanionHeaders.REQUEST_ID, UUID.randomUUID().toString());
            headers.set(CompanionHeaders.CORRELATION_ID, UUID.randomUUID().toString());
        }

        return headers;
    }
}
