package zw.gov.mohcc.impilo.pct.integration;

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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-through trauma blood-readiness gate over the MADI blood order (G1.10, mirrors theatre J-CS-1).
 * MADI is the blood system of record; PCT reads {@code GET /internal/v1/madi/orders/{orderId}} and
 * projects readiness from the order status — HONESTLY BLOCKED until MADI reports a real RESERVED order
 * with a COMPATIBLE crossmatch, then READY. Never fabricates RESERVED/COMPATIBLE when MADI is down.
 */
@Service
public class MadiBloodReadinessClient {

    private static final Logger log = LoggerFactory.getLogger(MadiBloodReadinessClient.class);
    private static final Set<String> RESERVED_STATES = Set.of("RESERVED", "ISSUED", "COMPLETED");

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MadiBloodReadinessClient(RestTemplate restTemplate,
                                    @Value("${pct.integration.madi.base-url:http://localhost:8300}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * @return {@code {status: READY|BLOCKED|UNAVAILABLE|NO_BLOOD_REQUEST, reservation_status,
     *         crossmatch_status, madi_status, trauma_episode_id, blocker?}}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readiness(String orderId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (orderId == null || orderId.isBlank()) {
            out.put("status", "NO_BLOOD_REQUEST");
            out.put("blocker", "no MADI blood order linked to this episode");
            return out;
        }
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(baseUrl + "/internal/v1/madi/orders/" + orderId,
                    HttpMethod.GET, new HttpEntity<>(trustHeaders()), Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return unavailable(out, orderId);
            }
            Object orderObj = resp.getBody().get("order");
            Map<String, Object> order = orderObj instanceof Map ? (Map<String, Object>) orderObj : resp.getBody();
            String madiStatus = str(order, "status");
            out.put("madi_status", madiStatus);
            out.put("trauma_episode_id", str(order, "traumaEpisodeId"));
            boolean reserved = madiStatus != null && RESERVED_STATES.contains(madiStatus);
            // MADI drives RESERVED only on a COMPATIBLE crossmatch, so RESERVED ⇒ COMPATIBLE.
            out.put("reservation_status", reserved ? "RESERVED"
                    : ("CROSSMATCH_PENDING".equals(madiStatus) ? "PENDING" : null));
            out.put("crossmatch_status", reserved ? "COMPATIBLE"
                    : ("CROSSMATCH_PENDING".equals(madiStatus) ? "PENDING" : null));
            if (reserved) {
                out.put("status", "READY");
            } else {
                out.put("status", "BLOCKED");
                out.put("blocker", "blood not ready — needs RESERVED + COMPATIBLE crossmatch (MADI status " + madiStatus + ")");
            }
            return out;
        } catch (RestClientException e) {
            log.warn("MADI unavailable reading blood order {}: {} — readiness stays honestly UNAVAILABLE", orderId, e.getMessage());
            return unavailable(out, orderId);
        }
    }

    private Map<String, Object> unavailable(Map<String, Object> out, String orderId) {
        out.put("status", "UNAVAILABLE");
        out.put("blocker", "MADI unavailable — cannot confirm blood readiness (no fabricated RESERVED)");
        return out;
    }

    private HttpHeaders trustHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Pod-ID", "national-spine");
        h.set("X-Request-ID", UUID.randomUUID().toString());
        try {
            TrustContext ctx = TrustContextHolder.require();
            if (ctx.tenantId() != null) h.set("X-Tenant-ID", ctx.tenantId().toString());
            if (ctx.actorId() != null) h.set("X-Actor-ID", ctx.actorId());
            h.set("X-Correlation-ID", ctx.correlationId() != null ? ctx.correlationId().toString() : UUID.randomUUID().toString());
            if (ctx.facilityId() != null) h.set("X-Facility-ID", ctx.facilityId().toString());
            if (ctx.purposeOfUse() != null) h.set("X-Purpose-Of-Use", ctx.purposeOfUse());
        } catch (IllegalStateException e) {
            h.set("X-Correlation-ID", UUID.randomUUID().toString());
        }
        h.set("X-Actor-Type", "SERVICE");
        return h;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v == null ? null : v.toString();
    }
}
