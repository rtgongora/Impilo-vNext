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

    // ── Staffing: roster week, on-call rota, shift swaps (V009) ──────────────
    //
    // Repointed off TusoServiceClient, which called /v1/staffing/* — paths no service in the
    // estate serves, so the roster screen, on-call screen and control tower rendered empty while
    // every layer reported success. Vashandi owns rostering (vsh_roster/vsh_shift); scheduled
    // on-call is a projection over shifts carrying an on_call_role, not a second store.

    public JsonNode getRosterWeek(String facilityId, String weekStart) {
        return unwrap(get("/staffing/roster-week",
                Map.of("facility_id", facilityId, "week_start", weekStart)));
    }

    public JsonNode listOnCall(String facilityId, String weekStart) {
        return unwrap(get("/staffing/on-call",
                Map.of("facility_id", facilityId, "week_start", weekStart)));
    }

    public JsonNode listSwapRequests(String facilityId) {
        return unwrap(get("/staffing/on-call/swaps", Map.of("facility_id", facilityId)));
    }

    public JsonNode createSwapRequest(Object body) {
        return unwrap(post("/staffing/on-call/swaps", body));
    }

    public JsonNode updateSwapRequest(String swapId, Object body) {
        return unwrap(patch("/staffing/on-call/swaps/" + swapId, body));
    }

    public JsonNode listWorkforceProfiles(Map<String, String> queryParams) {
        return unwrap(get("/workforce-profiles", queryParams));
    }

    public JsonNode getWorkforceProfile(String id) {
        return unwrap(get("/workforce-profiles/" + id, Map.of()));
    }

    /** Workforce attendance events for a worker (by provider-worker-id) in a month — ERP-HR view. */
    public JsonNode getAttendanceEventsByProvider(String providerWorkerId, int year, int month) {
        return unwrap(get("/attendance/events", Map.of(
                "provider_worker_id", providerWorkerId,
                "year", String.valueOf(year),
                "month", String.valueOf(month))));
    }

    /** Workforce leave windows for a worker (by provider-worker-id) — ERP-HR view. */
    public JsonNode getLeaveByProvider(String providerWorkerId) {
        return unwrap(get("/availability/by-provider", Map.of("provider_worker_id", providerWorkerId)));
    }

    /** Leave-type catalog (tenant-scoped) — owned by Vashandi. */
    public JsonNode getLeaveTypes() {
        return unwrap(get("/leave/types", Map.of()));
    }

    public JsonNode reconcileWorkforceProfile(Object body) {
        return unwrap(post("/workforce-profiles/reconcile", body));
    }

    /**
     * Workforce recognition read-model by Health ID — {recognised, cadre,
     * profession}. Enumeration-resistant upstream; null on transport failure
     * so callers fail open to "not recognised".
     */
    public JsonNode getRecognitionByHealthId(String healthId) {
        return unwrap(get("/workforce/by-health-id/" + healthId + "/recognition", Map.of()));
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

    /** Virtual-pool duty snapshot (who is on duty now); null when vashandi is unreachable. */
    public JsonNode getVirtualPoolOnDuty(String poolId) {
        return unwrap(get("/virtual-pools/" + poolId + "/on-duty", Map.of()));
    }

    /** Batch virtual-pool coverage (onDutyCount + nextShiftStart per pool); null when unreachable. */
    public JsonNode getVirtualPoolCoverage(String poolIdsCsv) {
        return unwrap(get("/virtual-pools/coverage", Map.of("poolIds", poolIdsCsv)));
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
