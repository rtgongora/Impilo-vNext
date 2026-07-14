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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Best-effort client to NHUME (dispatch, 8210) so intra-op transport (patient movement, specimen legs,
 * blood-product carriage) routes through the canonical delivery engine. NHUME is the SoR for the delivery —
 * inpatient/theatre only records the delivery id + a status projection. Mirrors {@link OrosOrderClient}.
 * Never fabricates a delivery: an outage returns an UNAVAILABLE view.
 */
@Service
public class NhumeTransportClient {

    private static final Logger log = LoggerFactory.getLogger(NhumeTransportClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NhumeTransportClient(RestTemplate inpatientRestTemplate,
                                @Value("${inpatient.integration.nhume.base-url:http://localhost:8210}") String baseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
    }

    /** Projection of a NHUME delivery. */
    public record DeliveryView(String deliveryId, String status, OffsetDateTime slaDueAt, boolean available) {
        static DeliveryView unavailable() { return new DeliveryView(null, null, null, false); }
    }

    /**
     * Create a NHUME delivery for a transport leg.
     *
     * @param deliveryType PATIENT | LAB_SAMPLE_PICKUP | BLOOD_PRODUCT
     * @param subjectRef   patient cpid (movement) / specimen ref (specimen) / blood order ref
     */
    @SuppressWarnings("unchecked")
    public DeliveryView createDelivery(String deliveryType, String leg, String subjectRef, String episodeRef,
                                       String facilityRef, String idempotencyKey) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("delivery_type", deliveryType);
            payload.put("priority", "URGENT");
            payload.put("request_source", "THEATRE");
            payload.put("requesting_facility_id", facilityRef);
            payload.put("clinical_context_ref", episodeRef);
            payload.put("notes", "Theatre transport leg: " + leg);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("leg", leg);
            if (subjectRef != null) metadata.put("subjectRef", subjectRef);
            payload.put("metadata", metadata);
            if ("LAB_SAMPLE_PICKUP".equals(deliveryType)) {
                payload.put("specimen", true);
                payload.put("chain_of_custody_required", true);
            }
            payload.put("submit_immediately", true);
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/internal/v1/nhume/deliveries",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            DeliveryView view = toView(resp.getBody());
            if (view.deliveryId() != null) {
                log.info("INPATIENT-THEATRE: created NHUME {} delivery {} (leg {})", deliveryType, view.deliveryId(), leg);
            }
            return view;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: NHUME unavailable creating {} delivery (leg {}, best-effort): {}",
                    deliveryType, leg, e.getMessage());
            return DeliveryView.unavailable();
        }
    }

    /** Read-through the current NHUME delivery status (used by the reconciler for sync + overdue detection). */
    @SuppressWarnings("unchecked")
    public DeliveryView getDelivery(String deliveryId) {
        if (deliveryId == null || deliveryId.isBlank()) return DeliveryView.unavailable();
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/internal/v1/nhume/deliveries/" + deliveryId,
                    HttpMethod.GET, new HttpEntity<>(trustHeaders(null)), Map.class);
            return toView(resp.getBody());
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: NHUME delivery {} read unavailable (best-effort): {}", deliveryId, e.getMessage());
            return DeliveryView.unavailable();
        }
    }

    @SuppressWarnings("unchecked")
    private static DeliveryView toView(Map<String, Object> body) {
        if (body == null) return DeliveryView.unavailable();
        Object data = body.containsKey("delivery_id") ? body : body.getOrDefault("data", body);
        if (!(data instanceof Map<?, ?> m)) return DeliveryView.unavailable();
        Map<String, Object> dm = (Map<String, Object>) m;
        Object id = dm.getOrDefault("delivery_id", dm.get("deliveryId"));
        Object status = dm.get("status");
        Object sla = dm.getOrDefault("sla_due_at", dm.get("slaDueAt"));
        OffsetDateTime slaDue = null;
        if (sla != null) {
            try { slaDue = OffsetDateTime.parse(sla.toString()); } catch (RuntimeException ignored) { /* leave null */ }
        }
        return new DeliveryView(id != null ? id.toString() : null,
                status != null ? status.toString() : null, slaDue, id != null);
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
