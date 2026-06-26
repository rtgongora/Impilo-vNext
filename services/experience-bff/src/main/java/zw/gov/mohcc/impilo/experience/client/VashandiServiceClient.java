package zw.gov.mohcc.impilo.experience.client;

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
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.vashandi.VashandiDtos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP client for Vashandi operational workforce service ({@code /v1/internal/vashandi/**}).
 * Trust headers are forwarded by {@link ServiceClientConfig#trustHeaderForwardingInterceptor()}.
 */
@Component
public class VashandiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(VashandiServiceClient.class);
    private static final String INTERNAL_PREFIX = "/v1/internal/vashandi";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public VashandiServiceClient(RestTemplate serviceRestTemplate,
                                 ServiceClientConfig.ServiceEndpoints endpoints,
                                 ObjectMapper objectMapper) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.vashandiBaseUrl();
        this.objectMapper = objectMapper;
    }

    public VashandiDtos.UpstreamResult get(String relativePath, Map<String, String> queryParams) {
        return exchange(HttpMethod.GET, relativePath, queryParams, null);
    }

    public VashandiDtos.UpstreamResult post(String relativePath, Object body) {
        return exchange(HttpMethod.POST, relativePath, Map.of(), body);
    }

    public VashandiDtos.UpstreamResult patch(String relativePath, Object body) {
        return exchange(HttpMethod.PATCH, relativePath, Map.of(), body);
    }

    public VashandiDtos.UpstreamResult put(String relativePath, Object body) {
        return exchange(HttpMethod.PUT, relativePath, Map.of(), body);
    }

    public VashandiDtos.UpstreamResult delete(String relativePath) {
        return exchange(HttpMethod.DELETE, relativePath, Map.of(), null);
    }

    public JsonNode listWorkforceProfiles(Map<String, String> queryParams) {
        return unwrap(get("/workforce-profiles", queryParams));
    }

    public JsonNode getWorkforceProfile(String id) {
        return unwrap(get("/workforce-profiles/" + id, Map.of()));
    }

    public JsonNode reconcileWorkforceProfile(Object body) {
        return unwrap(post("/workforce-profiles/reconcile", body));
    }

    public JsonNode fetchSessionContext(String healthId, String providerWorkerId) {
        Map<String, String> params = new LinkedHashMap<>();
        if (healthId != null && !healthId.isBlank()) {
            params.put("healthId", healthId);
        }
        if (providerWorkerId != null && !providerWorkerId.isBlank()) {
            params.put("providerWorkerId", providerWorkerId);
        }
        return unwrap(get("/workforce-profiles/session-context", params));
    }

    /**
     * C2 work-context read-model — active assignments + check-in state +
     * affiliations + requiresContextChooser for the WHERE/WHAT picker.
     * Returns null when upstream is unavailable; callers degrade honestly.
     */
    public JsonNode fetchWorkContext(String actorHealthId) {
        Map<String, String> params = new LinkedHashMap<>();
        if (actorHealthId != null && !actorHealthId.isBlank()) {
            params.put("actorId", actorHealthId);
        }
        return unwrap(get("/work-context", params));
    }

    private VashandiDtos.UpstreamResult exchange(HttpMethod method,
                                                 String relativePath,
                                                 Map<String, String> queryParams,
                                                 Object body) {
        try {
            String url = buildUrl(relativePath, queryParams);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = null;
            if (body != null) {
                String json = body instanceof String s ? s : objectMapper.writeValueAsString(body);
                entity = new HttpEntity<>(json, headers);
            }
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, method, entity, JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Vashandi {} {} failed: {}", method, relativePath, response.getStatusCode());
                return VashandiDtos.UpstreamResult.unavailable("HTTP " + response.getStatusCode().value());
            }
            JsonNode payload = response.getBody();
            JsonNode data = payload != null && payload.has("data") ? payload.path("data") : payload;
            return VashandiDtos.UpstreamResult.live(data);
        } catch (HttpStatusCodeException ex) {
            log.warn("Vashandi {} {} HTTP error: {}", method, relativePath, ex.getStatusCode());
            return VashandiDtos.UpstreamResult.unavailable(ex.getStatusText());
        } catch (Exception ex) {
            log.warn("Vashandi {} {} error: {}", method, relativePath, ex.getMessage());
            return VashandiDtos.UpstreamResult.unavailable(ex.getMessage());
        }
    }

    private String buildUrl(String relativePath, Map<String, String> queryParams) {
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        StringBuilder url = new StringBuilder(trimSlash(baseUrl)).append(INTERNAL_PREFIX).append(path);
        if (queryParams != null && !queryParams.isEmpty()) {
            url.append("?");
            queryParams.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    url.append(key).append("=").append(value).append("&");
                }
            });
            if (url.charAt(url.length() - 1) == '&') {
                url.setLength(url.length() - 1);
            }
        }
        return url.toString();
    }

    private static JsonNode unwrap(VashandiDtos.UpstreamResult result) {
        return result != null ? result.data() : null;
    }

    private static String trimSlash(String base) {
        if (base == null) {
            return "";
        }
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
}
