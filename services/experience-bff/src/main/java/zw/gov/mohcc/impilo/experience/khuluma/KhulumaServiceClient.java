package zw.gov.mohcc.impilo.experience.khuluma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

/**
 * BFF client for khuluma-service (the Comms Hub SoR). Mirrors {@code VashandiServiceClient}: uses the
 * shared {@code serviceRestTemplate} whose interceptor already forwards the v1.1 companion + actor
 * trust headers (and {@code Idempotency-Key}). This client is a transparent proxy — it preserves
 * khuluma-service's HTTP status semantics (e.g. 403 membership-deny, 404 not-found, 201 created) so
 * the BFF controller relays them faithfully rather than masking them.
 */
@Component
public class KhulumaServiceClient {

    private static final Logger log = LoggerFactory.getLogger(KhulumaServiceClient.class);
    private static final String PREFIX = "/internal/v1/khuluma";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public KhulumaServiceClient(RestTemplate serviceRestTemplate,
                                ServiceClientConfig.ServiceEndpoints endpoints,
                                ObjectMapper objectMapper) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.khulumaBaseUrl();
        this.objectMapper = objectMapper;
    }

    /** A relayed upstream result: the downstream HTTP status + JSON body. */
    public record Result(int status, JsonNode body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    public Result get(String relativePath, Map<String, String> query) {
        return exchange(HttpMethod.GET, relativePath, query, null);
    }

    public Result post(String relativePath, Object body) {
        return exchange(HttpMethod.POST, relativePath, Map.of(), body);
    }

    public Result put(String relativePath, Object body) {
        return exchange(HttpMethod.PUT, relativePath, Map.of(), body);
    }

    public Result delete(String relativePath) {
        return exchange(HttpMethod.DELETE, relativePath, Map.of(), null);
    }

    private Result exchange(HttpMethod method, String relativePath, Map<String, String> query, Object body) {
        try {
            String url = buildUrl(relativePath, query);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // khuluma-service's companion filter requires an Idempotency-Key on mutations; ensure one
            // exists. The trust interceptor overwrites it with the inbound key when the caller sent one.
            if (isMutation(method)) {
                headers.set(CompanionHeaders.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
            }
            HttpEntity<String> entity = body != null
                    ? new HttpEntity<>(objectMapper.writeValueAsString(body), headers)
                    : new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, method, entity, JsonNode.class);
            return new Result(response.getStatusCode().value(), response.getBody());
        } catch (HttpStatusCodeException ex) {
            // Preserve the downstream status + error envelope (403/404/400/401 are meaningful).
            return new Result(ex.getStatusCode().value(), parse(ex.getResponseBodyAsString()));
        } catch (Exception ex) {
            log.warn("Khuluma {} {} unavailable: {}", method, relativePath, ex.getMessage());
            return new Result(502, errorNode(ex.getMessage()));
        }
    }

    private static boolean isMutation(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    private String buildUrl(String relativePath, Map<String, String> query) {
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        StringBuilder url = new StringBuilder(trimSlash(baseUrl)).append(PREFIX).append(path);
        if (query != null && !query.isEmpty()) {
            url.append("?");
            query.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    url.append(k).append("=").append(v).append("&");
                }
            });
            if (url.charAt(url.length() - 1) == '&') {
                url.setLength(url.length() - 1);
            }
        }
        return url.toString();
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return errorNode(raw);
        }
    }

    private JsonNode errorNode(String message) {
        var node = objectMapper.createObjectNode();
        var error = node.putObject("error");
        error.put("code", "KHULUMA_UNAVAILABLE");
        error.put("message", message != null ? message : "khuluma-service unavailable");
        return node;
    }

    private static String trimSlash(String base) {
        if (base == null) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
