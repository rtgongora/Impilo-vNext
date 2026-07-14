package zw.gov.mohcc.impilo.inpatient.integration;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owner-routed readiness queries for theatre booking. Each method asks the OWNER service whether a
 * resource is ready and returns a {@link ReadinessResult} carrying the owner's answer + blockers.
 * Every call is best-effort: a downstream outage yields {@code UNAVAILABLE} with an explicit blocker —
 * it NEVER fabricates a READY result (no fake availability). The owners remain the systems of record:
 *  - asset-registry  → equipment readiness ({@code GET /internal/v1/equipment/readiness})
 *  - varapi          → provider scope/privilege (surgeon/anaesthetist/scrub)
 *  - vashandi        → team roster/shift availability
 *  - madi (via OROS) → blood readiness is placed as an OROS BLOOD_BANK order (see OrosOrderClient)
 */
@Service
public class TheatreReadinessClient {

    private static final Logger log = LoggerFactory.getLogger(TheatreReadinessClient.class);

    private final RestTemplate restTemplate;
    private final String assetBaseUrl;
    private final String varapiBaseUrl;
    private final String vashandiBaseUrl;

    public TheatreReadinessClient(
            RestTemplate inpatientRestTemplate,
            @Value("${inpatient.integration.asset-registry.base-url:http://localhost:8092}") String assetBaseUrl,
            @Value("${inpatient.integration.varapi.base-url:http://localhost:8083}") String varapiBaseUrl,
            @Value("${inpatient.integration.vashandi.base-url:http://localhost:8087}") String vashandiBaseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.assetBaseUrl = assetBaseUrl;
        this.varapiBaseUrl = varapiBaseUrl;
        this.vashandiBaseUrl = vashandiBaseUrl;
    }

    /** Outcome of an owner-routed readiness check. status: READY | BLOCKED | UNAVAILABLE. */
    public record ReadinessResult(String status, String ownerRef, List<Map<String, String>> blockers,
                                  Map<String, Object> detail) {
        public boolean ready() { return "READY".equals(status); }
        public static ReadinessResult ready(String ownerRef, Map<String, Object> detail) {
            return new ReadinessResult("READY", ownerRef, List.of(), detail);
        }
        public static ReadinessResult blocked(String code, String message, Map<String, Object> detail) {
            return new ReadinessResult("BLOCKED", null, List.of(Map.of("code", code, "message", message)), detail);
        }
        public static ReadinessResult unavailable(String owner) {
            return new ReadinessResult("UNAVAILABLE", null,
                    List.of(Map.of("code", "OWNER_UNAVAILABLE",
                            "message", owner + " unavailable — readiness could not be confirmed")), Map.of());
        }
    }

    /** Equipment readiness from asset-registry for the facility (owner: asset-registry). */
    @SuppressWarnings("unchecked")
    public ReadinessResult checkEquipment(String facilityRef) {
        if (facilityRef == null || facilityRef.isBlank()) {
            return ReadinessResult.blocked("NO_FACILITY", "No facility context for equipment readiness", Map.of());
        }
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    assetBaseUrl + "/internal/v1/equipment/readiness?facility_ref=" + facilityRef,
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(trustHeaders()), Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                return ReadinessResult.unavailable("asset-registry");
            }
            // asset-registry returns a readiness map; "ready"/"met" semantics. Treat explicit ready flag,
            // else infer from any unmet requirements list.
            Object ready = body.getOrDefault("ready", body.get("met"));
            boolean isReady = Boolean.TRUE.equals(ready)
                    || "READY".equalsIgnoreCase(String.valueOf(body.get("status")));
            Object gaps = body.getOrDefault("gaps", body.get("unmet"));
            if (!isReady && gaps instanceof List<?> g && !g.isEmpty()) {
                return new ReadinessResult("BLOCKED", null,
                        List.of(Map.of("code", "EQUIPMENT_GAP",
                                "message", "Equipment requirements unmet: " + g)), body);
            }
            return isReady ? ReadinessResult.ready("asset-registry:" + facilityRef, body)
                    : ReadinessResult.blocked("EQUIPMENT_NOT_READY",
                    "Equipment readiness not confirmed by asset-registry", body);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: asset-registry readiness unavailable for {} (best-effort): {}",
                    facilityRef, e.getMessage());
            return ReadinessResult.unavailable("asset-registry");
        }
    }

    /** Provider scope check via varapi: provider must hold an ACTIVE privilege matching the role. */
    @SuppressWarnings("unchecked")
    public ReadinessResult checkProviderScope(String providerId, String requiredPrivilege) {
        if (providerId == null || providerId.isBlank()) {
            return ReadinessResult.blocked("NO_PROVIDER", "No " + requiredPrivilege + " assigned", Map.of());
        }
        try {
            // Wave 1 fix: varapi PrivilegeController wraps the list in an ApiResponse envelope
            // ({data:[...]}). Deserialising straight to List silently failed → UNAVAILABLE. Read the
            // envelope and unwrap .data (falls back to a raw list body).
            ResponseEntity<Map> resp = restTemplate.exchange(
                    varapiBaseUrl + "/v1/internal/providers/" + providerId + "/privileges",
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(trustHeaders()), Map.class);
            Map<String, Object> envelope = resp.getBody();
            if (envelope == null) {
                return ReadinessResult.unavailable("varapi");
            }
            Object data = envelope.getOrDefault("data", envelope.get("privileges"));
            if (!(data instanceof List<?> rawList)) {
                return ReadinessResult.unavailable("varapi");
            }
            List<Map<String, Object>> privileges = (List<Map<String, Object>>) (List<?>) rawList;
            // Wave 1 fix: the varapi privilege lifecycle has no ACTIVE status — it uses APPROVED.
            boolean ok = privileges.stream().anyMatch(p ->
                    "APPROVED".equalsIgnoreCase(String.valueOf(p.getOrDefault("status", p.get("state"))))
                            && privilegeMatches(p, requiredPrivilege));
            return ok
                    ? ReadinessResult.ready("varapi:" + providerId, Map.of("provider_id", providerId))
                    : ReadinessResult.blocked("SCOPE_NOT_ACTIVE",
                    providerId + " lacks an ACTIVE " + requiredPrivilege + " privilege",
                    Map.of("provider_id", providerId, "required", requiredPrivilege));
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: varapi scope unavailable for {} (best-effort): {}",
                    providerId, e.getMessage());
            return ReadinessResult.unavailable("varapi");
        }
    }

    private static boolean privilegeMatches(Map<String, Object> p, String required) {
        String type = String.valueOf(p.getOrDefault("privilegeType", p.getOrDefault("type", "")));
        String scope = String.valueOf(p.getOrDefault("scope", ""));
        return required.equalsIgnoreCase(type) || required.equalsIgnoreCase(scope)
                || type.toUpperCase().contains(required.toUpperCase());
    }

    /** Team availability via vashandi rosters for the facility (owner: vashandi). */
    @SuppressWarnings("unchecked")
    public ReadinessResult checkTeamAvailability(String facilityRef) {
        if (facilityRef == null || facilityRef.isBlank()) {
            return ReadinessResult.blocked("NO_FACILITY", "No facility context for team availability", Map.of());
        }
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    vashandiBaseUrl + "/v1/internal/vashandi/rosters?facility_ref=" + facilityRef + "&status=PUBLISHED",
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(trustHeaders()), Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                return ReadinessResult.unavailable("vashandi");
            }
            Object content = body.getOrDefault("content", body.get("rosters"));
            boolean hasPublishedRoster = content instanceof List<?> l && !l.isEmpty();
            return hasPublishedRoster
                    ? ReadinessResult.ready("vashandi:" + facilityRef, body)
                    : ReadinessResult.blocked("NO_PUBLISHED_ROSTER",
                    "No published theatre roster confirms team availability", body);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: vashandi roster unavailable for {} (best-effort): {}",
                    facilityRef, e.getMessage());
            return ReadinessResult.unavailable("vashandi");
        }
    }

    private HttpHeaders trustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            TrustContext ctx = TrustContextHolder.require();
            if (ctx.tenantId() != null) headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            if (ctx.actorId() != null) headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            if (ctx.correlationId() != null) headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.facilityId() != null) headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            if (ctx.purposeOfUse() != null) headers.set(TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse());
        } catch (IllegalStateException ignored) {
            // best-effort
        }
        return headers;
    }
}
