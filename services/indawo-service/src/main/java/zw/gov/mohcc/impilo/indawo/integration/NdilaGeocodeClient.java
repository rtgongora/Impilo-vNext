package zw.gov.mohcc.impilo.indawo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client to ndila-service, the platform geography/geocoding system-of-record (R6/G21). Indawo does
 * NOT own a geocoder — per the registry it delegates address geocoding here rather than embedding a
 * parallel provider stack. Best-effort: a down/failed geocode returns {@code null} so address create
 * still succeeds with a manual/unverified location (we never fabricate coordinates).
 */
@Component
public class NdilaGeocodeClient {

    private static final Logger log = LoggerFactory.getLogger(NdilaGeocodeClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NdilaGeocodeClient(RestTemplate restTemplate,
                              @Value("${impilo.services.ndila.base-url:http://localhost:8155}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /** A resolved geocode: coordinates + a coarse quality band + whether ndila verified it. */
    public record GeocodeResult(BigDecimal latitude, BigDecimal longitude, String quality, boolean verified) {}

    /**
     * Geocode a free-text address query, scoped by province/district. Returns {@code null} when
     * ndila is unavailable or found no match — the caller keeps the address without coordinates.
     */
    public GeocodeResult geocode(String query, String province, String district) {
        if (query == null || query.isBlank()) {
            return null;
        }
        RequestContext ctx = RequestContextHolder.get();
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("query", query);
        req.put("country", "ZWE");
        if (province != null && !province.isBlank()) req.put("province", province);
        if (district != null && !district.isBlank()) req.put("district", district);
        req.put("purposeOfUse", "SITE_REGISTRATION");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            forwardTrust(headers, ctx);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    baseUrl + "/api/v1/ndila/geocode", HttpMethod.POST, new HttpEntity<>(req, headers), JsonNode.class);
            JsonNode body = response.getBody();
            JsonNode data = body != null && body.has("data") ? body.get("data") : body;
            if (data == null || !data.path("success").asBoolean(false)) {
                return null;
            }
            JsonNode first = data.path("results").isArray() && data.path("results").size() > 0
                    ? data.path("results").get(0) : null;
            if (first == null || !first.hasNonNull("latitude") || !first.hasNonNull("longitude")) {
                return null;
            }
            double confidence = first.path("confidence").asDouble(0.0);
            String quality = confidence >= 0.8 ? "HIGH" : confidence >= 0.5 ? "MEDIUM" : "LOW";
            boolean verified = "VERIFIED".equalsIgnoreCase(first.path("verificationStatus").asText(""));
            return new GeocodeResult(
                    new BigDecimal(first.get("latitude").asText()),
                    new BigDecimal(first.get("longitude").asText()),
                    quality, verified);
        } catch (Exception e) {
            log.warn("Ndila geocode failed for '{}': {}", query, e.getMessage());
            return null;
        }
    }

    private static void forwardTrust(HttpHeaders headers, RequestContext ctx) {
        if (ctx == null) return;
        if (ctx.tenantId() != null) headers.set(CompanionHeaders.TENANT_ID, ctx.tenantId());
        if (ctx.podId() != null) headers.set(CompanionHeaders.POD_ID, ctx.podId());
        if (ctx.requestId() != null) headers.set(CompanionHeaders.REQUEST_ID, ctx.requestId());
        if (ctx.correlationId() != null) headers.set(CompanionHeaders.CORRELATION_ID, ctx.correlationId());
        if (ctx.authToken() != null && !ctx.authToken().isBlank()) {
            headers.set(CompanionHeaders.AUTHORIZATION, ctx.authToken());
        }
    }
}
