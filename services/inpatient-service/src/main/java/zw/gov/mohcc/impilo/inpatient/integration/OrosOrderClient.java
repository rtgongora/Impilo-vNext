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

import java.util.List;
import java.util.Map;

/**
 * Thin client to OROS (orders, 8089) so an inpatient ward order routes through the canonical orders
 * engine (OROS is the SoR — inpatient never owns orders). Mirrors PCT's OrosIntegration. Used for
 * inpatient diagnostic/medication/diet/consult orders placed from the ward.
 */
@Service
public class OrosOrderClient {

    private static final Logger log = LoggerFactory.getLogger(OrosOrderClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OrosOrderClient(RestTemplate inpatientRestTemplate,
                           @Value("${inpatient.integration.oros.base-url:http://localhost:8089}") String baseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Place an order in OROS. Returns the OROS order id, or null if OROS was unavailable.
     *
     * @param orderType   LAB | IMAGING | PHARMACY | PROCEDURE | BLOOD_BANK | OTHER
     * @param priority    ROUTINE | URGENT | STAT
     * @param items       order items (each: code, displayName, quantity, instructions)
     */
    @SuppressWarnings("unchecked")
    public String placeOrder(String orderType, String priority, String patientCpid, String encounterRef,
                             String clinicalNotes, List<Map<String, Object>> items) {
        try {
            Map<String, Object> payload = Map.of(
                    "orderType", orderType,
                    "priority", priority != null ? priority : "ROUTINE",
                    "patientCpid", patientCpid,
                    "encounterRef", encounterRef,
                    "clinicalNotes", clinicalNotes != null ? clinicalNotes : "",
                    "items", items);
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/v1/orders",
                    new HttpEntity<>(payload, trustHeaders()), Map.class);
            Map<String, Object> body = resp.getBody();
            Object data = body != null ? body.getOrDefault("data", body) : null;
            if (data instanceof Map<?, ?> m) {
                Object id = ((Map<String, Object>) m).get("orderId");
                if (id == null) id = ((Map<String, Object>) m).get("id");
                log.info("INPATIENT: placed OROS {} order for {} -> {}", orderType, patientCpid, id);
                return id != null ? id.toString() : null;
            }
            return null;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT: OROS unavailable placing {} order for {} (best-effort): {}",
                    orderType, patientCpid, e.getMessage());
            return null;
        }
    }

    /**
     * Cancel an OROS order so the owner releases its own reservation/state (e.g. when a theatre case is
     * cancelled). Best-effort: an OROS outage must not block cancelling the theatre case.
     *
     * @return true if OROS acknowledged the cancellation.
     */
    public boolean cancelOrder(String orderId, String reason) {
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> payload = Map.of("reason", reason != null ? reason : "Cancelled");
            restTemplate.postForEntity(baseUrl + "/v1/orders/" + orderId + "/cancel",
                    new HttpEntity<>(payload, trustHeaders()), Map.class);
            log.info("INPATIENT: cancelled OROS order {} ({})", orderId, reason);
            return true;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT: OROS unavailable cancelling order {} (best-effort): {}", orderId, e.getMessage());
            return false;
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
