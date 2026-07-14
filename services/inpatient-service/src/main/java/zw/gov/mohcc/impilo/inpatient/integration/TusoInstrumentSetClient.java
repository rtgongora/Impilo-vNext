package zw.gov.mohcc.impilo.inpatient.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Best-effort client to TUSO (registry, 8084) for the instrument-set CSSD lifecycle. TUSO is the SoR for
 * the instrument_set registry + its sterilisation state (tuso V024/V026) — theatre only records the set
 * id + an issue/return projection. Mirrors {@link TheatreReadinessClient}: it NEVER fabricates a STERILE
 * set; an outage returns an empty list / UNAVAILABLE view.
 */
@Service
public class TusoInstrumentSetClient {

    private static final Logger log = LoggerFactory.getLogger(TusoInstrumentSetClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TusoInstrumentSetClient(RestTemplate inpatientRestTemplate,
                                   @Value("${inpatient.integration.tuso.base-url:http://localhost:8084}") String baseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
    }

    /** Projection of a TUSO instrument set. */
    public record SetView(String id, String name, String sterilisationState, OffsetDateTime sterileUntil,
                          boolean available) {
        public boolean sterile() { return "STERILE".equals(sterilisationState); }
        static SetView unavailable() { return new SetView(null, null, null, null, false); }
    }

    /** List STERILE instrument sets in the tenant (used by the readiness gate to pick a real set). */
    @SuppressWarnings("unchecked")
    public List<SetView> listSterileSets() {
        List<SetView> out = new ArrayList<>();
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/v1/internal/tuso/instrument-sets?state=STERILE",
                    HttpMethod.GET, new HttpEntity<>(trustHeaders(null)), Map.class);
            Object data = unwrap(resp.getBody());
            if (data instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) out.add(toView((Map<String, Object>) m));
                }
            }
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: TUSO instrument-set list unavailable (best-effort): {}", e.getMessage());
        }
        return out;
    }

    /** Read a single set's current CSSD view. */
    @SuppressWarnings("unchecked")
    public SetView getSet(String setId) {
        if (setId == null) return SetView.unavailable();
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/v1/internal/tuso/instrument-sets/" + setId + "/cssd",
                    HttpMethod.GET, new HttpEntity<>(trustHeaders(null)), Map.class);
            Object data = unwrap(resp.getBody());
            return data instanceof Map<?, ?> m ? toView((Map<String, Object>) m) : SetView.unavailable();
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: TUSO set {} read unavailable (best-effort): {}", setId, e.getMessage());
            return SetView.unavailable();
        }
    }

    /** Issue a STERILE set to a theatre case (TUSO: STERILE → IN_USE). Returns the set view after issue. */
    @SuppressWarnings("unchecked")
    public SetView issue(String setId, String episodeRef, String issuedTo, String idempotencyKey) {
        if (setId == null) return SetView.unavailable();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("episodeRef", episodeRef);
            if (issuedTo != null) payload.put("issuedTo", issuedTo);
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    baseUrl + "/v1/internal/tuso/instrument-sets/" + setId + "/issue",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            Object data = unwrap(resp.getBody());
            log.info("INPATIENT-THEATRE: issued TUSO instrument set {} to case {}", setId, episodeRef);
            return data instanceof Map<?, ?> m ? toView((Map<String, Object>) m) : SetView.unavailable();
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: TUSO set {} issue unavailable (best-effort): {}", setId, e.getMessage());
            return SetView.unavailable();
        }
    }

    /** Return a set from a case (TUSO: IN_USE → SOILED), flagging contaminated/incomplete. */
    @SuppressWarnings("unchecked")
    public SetView returnSet(String setId, boolean contaminated, boolean incomplete, String idempotencyKey) {
        if (setId == null) return SetView.unavailable();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("contaminated", contaminated);
            payload.put("incomplete", incomplete);
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    baseUrl + "/v1/internal/tuso/instrument-sets/" + setId + "/return",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            Object data = unwrap(resp.getBody());
            log.info("INPATIENT-THEATRE: returned TUSO instrument set {} (SOILED)", setId);
            return data instanceof Map<?, ?> m ? toView((Map<String, Object>) m) : SetView.unavailable();
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: TUSO set {} return unavailable (best-effort): {}", setId, e.getMessage());
            return SetView.unavailable();
        }
    }

    @SuppressWarnings("unchecked")
    private static SetView toView(Map<String, Object> m) {
        Object id = m.getOrDefault("id", m.get("instrumentSetId"));
        Object name = m.get("name");
        Object state = m.get("sterilisationState");
        Object until = m.get("sterileUntil");
        OffsetDateTime sterileUntil = null;
        if (until != null) {
            try { sterileUntil = OffsetDateTime.parse(until.toString()); } catch (RuntimeException ignored) { /* leave null */ }
        }
        return new SetView(id != null ? id.toString() : null,
                name != null ? name.toString() : null,
                state != null ? state.toString() : null,
                sterileUntil, id != null);
    }

    /** Unwrap the shared {data,...} ApiResponse envelope (falls back to the raw body). */
    private static Object unwrap(Map<String, Object> body) {
        if (body == null) return null;
        return body.containsKey("data") ? body.get("data") : body;
    }

    private HttpHeaders trustHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Pod-ID", System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "inpatient");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey);
        try {
            TrustContext ctx = TrustContextHolder.require();
            if (ctx.tenantId() != null) headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            if (ctx.actorId() != null) headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_ACTOR_TYPE, ctx.actorType() != null ? ctx.actorType() : "PROVIDER");
            headers.set(TrustContext.H_CORRELATION_ID,
                    ctx.correlationId() != null ? ctx.correlationId().toString() : UUID.randomUUID().toString());
            if (ctx.facilityId() != null) headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            headers.set(TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse() != null ? ctx.purposeOfUse() : "TREATMENT");
        } catch (IllegalStateException ignored) {
            headers.set(TrustContext.H_ACTOR_TYPE, "PROVIDER");
            headers.set(TrustContext.H_CORRELATION_ID, UUID.randomUUID().toString());
            headers.set(TrustContext.H_PURPOSE_OF_USE, "TREATMENT");
        }
        return headers;
    }
}
