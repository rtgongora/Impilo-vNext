package zw.gov.mohcc.impilo.oros.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;

/**
 * Read-only client for the TUSO facility/workspace registry, used to populate internal routing
 * destinations for the diagnostic provider directory.
 *
 * <p>Honest degradation: if TUSO is not configured ({@code base-url} blank) or unreachable,
 * {@link #searchFacilities()} returns an empty list rather than failing the directory.</p>
 */
@Service
public class TusoClient {

    private static final Logger log = LoggerFactory.getLogger(TusoClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TusoClient(RestTemplate restTemplate,
                      @Value("${oros.integration.tuso.base-url:http://localhost:8084}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public boolean isConfigured() {
        return !baseUrl.isBlank();
    }

    /** Return facility summaries ({@code id}, {@code name}/{@code facilityName}) as raw maps. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchFacilities() {
        if (!isConfigured()) {
            return List.of();
        }
        try {
            String url = baseUrl + "/v1/internal/facilities/search";
            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("page", 0, "size", 100), headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof Map<?, ?> paged) {
                    Object items = ((Map<String, Object>) paged).get("items");
                    if (items instanceof List<?> list) {
                        return (List<Map<String, Object>>) list;
                    }
                }
            }
            return List.of();
        } catch (RestClientException e) {
            log.warn("TUSO unavailable for facility directory: {}", e.getMessage());
            return List.of();
        }
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
            log.debug("No trust context available for TUSO call headers");
        }
        return headers;
    }
}
