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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Best-effort client to OROS (orders/lab, 8089) for theatre specimens. OROS is the SoR for the lab
 * order, specimen and result — theatre only records the peer ids + a status projection and mirrors the
 * critical-result escalation. Mirrors {@link OrosOrderClient}: it NEVER fabricates a specimen or result.
 */
@Service
public class OrosSpecimenClient {

    private static final Logger log = LoggerFactory.getLogger(OrosSpecimenClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OrosSpecimenClient(RestTemplate inpatientRestTemplate,
                              @Value("${inpatient.integration.oros.base-url:http://localhost:8089}") String baseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
    }

    public record SpecimenView(String specimenId, String status, boolean available) {
        static SpecimenView unavailable() { return new SpecimenView(null, null, false); }
    }

    public record ResultView(String resultId, boolean critical, String summary) {}

    /** Place a LAB order for a theatre specimen. Returns the OROS order id or null. */
    @SuppressWarnings("unchecked")
    public String placeLabOrder(String patientCpid, String encounterRef, String specimenLabel,
                                String specimenType, String bodySite, String idempotencyKey) {
        try {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", specimenType != null ? specimenType : "HISTOPATH");
            item.put("displayName", specimenLabel != null ? specimenLabel : "Theatre specimen");
            item.put("specimenType", specimenType);
            item.put("bodySite", bodySite);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderType", "LAB");
            payload.put("priority", "STAT");
            payload.put("patientCpid", patientCpid);
            payload.put("encounterRef", encounterRef);
            payload.put("clinicalNotes", "Theatre specimen: " + (specimenLabel != null ? specimenLabel : ""));
            payload.put("items", List.of(item));
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/v1/orders",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            Object data = unwrap(resp.getBody());
            if (data instanceof Map<?, ?> m) {
                Object id = ((Map<String, Object>) m).getOrDefault("orderId", ((Map<String, Object>) m).get("id"));
                return id != null ? id.toString() : null;
            }
            return null;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: OROS unavailable placing specimen LAB order for {} (best-effort): {}",
                    patientCpid, e.getMessage());
            return null;
        }
    }

    /** Record specimen collection against the OROS order. Returns the OROS specimen id + status. */
    @SuppressWarnings("unchecked")
    public SpecimenView collect(String orosOrderId, String specimenType, String bodySite, String idempotencyKey) {
        if (orosOrderId == null) return SpecimenView.unavailable();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("specimenType", specimenType);
            payload.put("bodySite", bodySite);
            payload.put("collectionSite", "THEATRE");
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    baseUrl + "/v1/orders/" + orosOrderId + "/specimens",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            return specimenView(unwrap(resp.getBody()));
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: OROS unavailable collecting specimen for order {} (best-effort): {}",
                    orosOrderId, e.getMessage());
            return SpecimenView.unavailable();
        }
    }

    /** Dispatch the specimen to the lab (after NHUME pickup). */
    public SpecimenView dispatch(String orosSpecimenId, String idempotencyKey) {
        return specimenTransition(orosSpecimenId, "dispatch", null, idempotencyKey);
    }

    /** Lab receipt of the specimen (RECEIVED). */
    public SpecimenView receive(String orosSpecimenId, String labNumber, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (labNumber != null) body.put("labNumber", labNumber);
        return specimenTransition(orosSpecimenId, "receive", body, idempotencyKey);
    }

    @SuppressWarnings("unchecked")
    private SpecimenView specimenTransition(String orosSpecimenId, String action, Map<String, Object> body,
                                            String idempotencyKey) {
        if (orosSpecimenId == null) return SpecimenView.unavailable();
        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    baseUrl + "/v1/specimens/" + orosSpecimenId + "/" + action,
                    new HttpEntity<>(body != null ? body : Map.of(), trustHeaders(idempotencyKey)), Map.class);
            return specimenView(unwrap(resp.getBody()));
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: OROS specimen {} unavailable for {} (best-effort): {}",
                    action, orosSpecimenId, e.getMessage());
            return SpecimenView.unavailable();
        }
    }

    /** Read the OROS results for an order (critical flag mirrored to a SAFETY escalation by the caller). */
    @SuppressWarnings("unchecked")
    public List<ResultView> getResults(String orosOrderId) {
        List<ResultView> out = new ArrayList<>();
        if (orosOrderId == null) return out;
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/v1/orders/" + orosOrderId + "/results",
                    HttpMethod.GET, new HttpEntity<>(trustHeaders(null)), Map.class);
            Object data = unwrap(resp.getBody());
            if (data instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Map<String, Object> rm = (Map<String, Object>) m;
                        Object id = rm.getOrDefault("resultId", rm.get("result_id"));
                        boolean critical = Boolean.TRUE.equals(rm.getOrDefault("isCritical", rm.get("is_critical")));
                        Object summary = rm.get("summary");
                        out.add(new ResultView(id != null ? id.toString() : null, critical,
                                summary != null ? summary.toString() : null));
                    }
                }
            }
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: OROS results unavailable for order {} (best-effort): {}",
                    orosOrderId, e.getMessage());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static SpecimenView specimenView(Object data) {
        if (!(data instanceof Map<?, ?> m)) return SpecimenView.unavailable();
        Map<String, Object> sm = (Map<String, Object>) m;
        Object id = sm.getOrDefault("specimenId", sm.get("specimen_id"));
        Object status = sm.get("status");
        return new SpecimenView(id != null ? id.toString() : null,
                status != null ? status.toString() : null, id != null);
    }

    /** Unwrap the shared {success,data,...} envelope (falls back to the raw body). */
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
            headers.set(TrustContext.H_CORRELATION_ID,
                    ctx.correlationId() != null ? ctx.correlationId().toString() : UUID.randomUUID().toString());
            if (ctx.facilityId() != null) headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            headers.set(TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse() != null ? ctx.purposeOfUse() : "TREATMENT");
        } catch (IllegalStateException ignored) {
            headers.set(TrustContext.H_CORRELATION_ID, UUID.randomUUID().toString());
            headers.set(TrustContext.H_PURPOSE_OF_USE, "TREATMENT");
        }
        return headers;
    }
}
