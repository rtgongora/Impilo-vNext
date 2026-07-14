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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Best-effort client to inventory-service (DURA, 8098) for the controlled-drug register. inventory owns
 * the register (two-person witness + running balance + reconciliation) — theatre records a register action
 * (ISSUE / ADMINISTER / WASTE / DISCARD) keyed to the case. The witness requirement is enforced by
 * inventory (400 without a witness); this client surfaces that failure honestly. Mirrors
 * {@link InventoryConsumptionClient}.
 */
@Service
public class InventoryControlledDrugClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryControlledDrugClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public InventoryControlledDrugClient(RestTemplate inpatientRestTemplate,
                                         @Value("${inpatient.integration.inventory.base-url:http://localhost:8098}") String baseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
    }

    /** Peer register-entry id + running balance returned by inventory. rejected=true when inventory 400s. */
    public record RegisterView(String entryId, String action, BigDecimal quantity, BigDecimal runningBalance,
                               String witnessProvider, boolean available, boolean rejected, String message) {
        static RegisterView unavailable() {
            return new RegisterView(null, null, null, null, null, false, false, null);
        }
        static RegisterView rejected(String msg) {
            return new RegisterView(null, null, null, null, null, false, true, msg);
        }
    }

    /**
     * Record a controlled-drug register action. inventory enforces the two-person witness on
     * ADMINISTER/WASTE/DISCARD — a rejected() view carries the 400 through so the theatre link is honest.
     */
    @SuppressWarnings("unchecked")
    public RegisterView record(String itemCode, String drugName, String action, BigDecimal quantity, String unit,
                               String administeringProvider, String witnessProvider, String episodeRef,
                               String chartEntryRef, String idempotencyKey) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("itemCode", itemCode);
            payload.put("drugName", drugName);
            payload.put("action", action);
            payload.put("quantity", quantity);
            payload.put("unit", unit);
            payload.put("administeringProvider", administeringProvider);
            payload.put("witnessProvider", witnessProvider);
            payload.put("refType", "THEATRE_CASE");
            payload.put("refId", episodeRef);
            payload.put("chartEntryRef", chartEntryRef);
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/v1/internal/controlled-drugs/record",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            Object data = unwrap(resp.getBody());
            if (data instanceof Map<?, ?> m) {
                Map<String, Object> dm = (Map<String, Object>) m;
                log.info("INPATIENT-THEATRE: controlled-drug {} {} recorded (entry {})", action, itemCode, dm.get("entryId"));
                return new RegisterView(str(dm, "entryId"), str(dm, "action"),
                        num(dm.get("quantity")), num(dm.get("runningBalance")),
                        str(dm, "witnessProvider"), dm.get("entryId") != null, false, null);
            }
            return RegisterView.unavailable();
        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest e) {
            // inventory rejected the action (e.g. witness missing) — surface it, do not swallow as unavailable.
            log.warn("INPATIENT-THEATRE: inventory rejected controlled-drug {} (witness required?): {}",
                    action, e.getMessage());
            return RegisterView.rejected(e.getResponseBodyAsString());
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: inventory controlled-drug register unavailable for {} (best-effort): {}",
                    itemCode, e.getMessage());
            return RegisterView.unavailable();
        }
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v != null ? v.toString() : null;
    }

    private static BigDecimal num(Object v) {
        if (v == null) return null;
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return null; }
    }

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
