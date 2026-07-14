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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thin, best-effort client to MADI (blood, 8300) so the theatre blood pipeline routes through the
 * canonical blood engine. MADI is the SoR for the blood order + transfusion episode — inpatient/theatre
 * only records the peer ids + a status projection. Mirrors {@link OrosOrderClient}/{@link TheatreReadinessClient}:
 * ctor-injected {@code inpatientRestTemplate} + {@code @Value} base-url, {@link #trustHeaders} copying the
 * TrustContext (plus synthesised pod/request/idempotency companion headers), best-effort try/catch that
 * returns null / an UNAVAILABLE view. It NEVER fabricates a RESERVED/COMPATIBLE answer.
 */
@Service
public class MadiBloodClient {

    private static final Logger log = LoggerFactory.getLogger(MadiBloodClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MadiBloodClient(RestTemplate inpatientRestTemplate,
                           @Value("${inpatient.integration.madi.base-url:http://localhost:8300}") String baseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
    }

    /** Read-through projection of a MADI blood order. reservation/crossmatch derived from the FSM status. */
    public record BloodOrderView(String status, String reservationStatus, String crossmatchStatus, boolean available) {
        public boolean reserved() {
            return "RESERVED".equals(status) || "ISSUED".equals(status) || "COMPLETED".equals(status);
        }
        static BloodOrderView unavailable() { return new BloodOrderView(null, null, null, false); }
    }

    /**
     * Place (and submit) a MADI blood order for group &amp; screen. Returns the MADI order id, or null if
     * MADI was unavailable.
     */
    @SuppressWarnings("unchecked")
    public String requestBlood(String patientCpid, String bloodGroup, String componentType, Integer units,
                               String orosOrderRef, String orderingProvider, String idempotencyKey) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("patient_cpid", patientCpid);
            payload.put("blood_group", bloodGroup != null ? bloodGroup : "UNKNOWN");
            payload.put("component_type", componentType != null ? componentType : "WHOLE_BLOOD");
            payload.put("units_requested", units != null ? units : 1);
            payload.put("ordering_provider", orderingProvider);
            if (orosOrderRef != null) payload.put("oros_order_ref", orosOrderRef);
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/internal/v1/madi/orders",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            String orderId = extractOrderId(resp.getBody());
            if (orderId != null) {
                // Submit so MADI advances DRAFT → SUBMITTED (group & screen underway).
                try {
                    restTemplate.postForEntity(baseUrl + "/internal/v1/madi/orders/" + orderId + "/submit",
                            new HttpEntity<>(Map.of(), trustHeaders(idempotencyKey)), Map.class);
                } catch (RestClientException ignored) { /* submit is advisory; order already exists */ }
                log.info("INPATIENT-THEATRE: requested MADI blood order {} for {}", orderId, patientCpid);
            }
            return orderId;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: MADI unavailable requesting blood for {} (best-effort): {}",
                    patientCpid, e.getMessage());
            return null;
        }
    }

    /** Read-through the current MADI order state (used by the readiness gate + reconciler). */
    @SuppressWarnings("unchecked")
    public BloodOrderView getOrderDetail(String madiOrderId) {
        if (madiOrderId == null || madiOrderId.isBlank()) return BloodOrderView.unavailable();
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/internal/v1/madi/orders/" + madiOrderId,
                    HttpMethod.GET, new HttpEntity<>(trustHeaders(null)), Map.class);
            Map<String, Object> body = resp.getBody();
            Object orderObj = body != null ? body.get("order") : null;
            if (!(orderObj instanceof Map<?, ?> order)) return BloodOrderView.unavailable();
            String status = str(((Map<String, Object>) order).get("status"));
            String reservation = ("RESERVED".equals(status) || "ISSUED".equals(status) || "COMPLETED".equals(status))
                    ? "RESERVED" : ("CROSSMATCH_PENDING".equals(status) ? "PENDING" : null);
            String crossmatch = ("RESERVED".equals(status) || "ISSUED".equals(status) || "COMPLETED".equals(status))
                    ? "COMPATIBLE" : ("CROSSMATCH_PENDING".equals(status) ? "PENDING" : null);
            return new BloodOrderView(status, reservation, crossmatch, true);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: MADI order {} read unavailable (best-effort): {}", madiOrderId, e.getMessage());
            return BloodOrderView.unavailable();
        }
    }

    /** Start a MADI transfusion episode against a (reserved/issued) blood order. Returns the episode id or null. */
    @SuppressWarnings("unchecked")
    public String startTransfusion(String madiOrderId, String patientCpid, String startedBy, String idempotencyKey) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("order_id", madiOrderId);
            payload.put("patient_cpid", patientCpid);
            payload.put("started_by", startedBy);
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/internal/v1/madi/transfusions",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            Map<String, Object> body = resp.getBody();
            Object id = body != null ? firstNonNull(body.get("episodeId"), body.get("episode_id")) : null;
            log.info("INPATIENT-THEATRE: started MADI transfusion episode {} for order {}", id, madiOrderId);
            return id != null ? id.toString() : null;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: MADI unavailable starting transfusion for order {} (best-effort): {}",
                    madiOrderId, e.getMessage());
            return null;
        }
    }

    /** Bedside two-step (patient + unit) identity verification before spiking the unit. */
    public boolean preVerify(String transfusionEpisodeId, String patientCpid, String bloodUnitId,
                             String patientMethod, String unitMethod, String verifiedBy, String idempotencyKey) {
        if (transfusionEpisodeId == null) return false;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("patient_cpid", patientCpid);
            payload.put("blood_unit_id", bloodUnitId);
            payload.put("patient_method", patientMethod != null ? patientMethod : "WRISTBAND");
            payload.put("unit_method", unitMethod != null ? unitMethod : "UNIT_SCAN");
            payload.put("verified_by", verifiedBy);
            restTemplate.postForEntity(baseUrl + "/internal/v1/madi/transfusions/" + transfusionEpisodeId + "/pre-verify",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            return true;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: MADI pre-verify unavailable for episode {} (best-effort): {}",
                    transfusionEpisodeId, e.getMessage());
            return false;
        }
    }

    /** Record a transfusion observation (obs/reaction) against the MADI episode. */
    public boolean recordObservation(String transfusionEpisodeId, String observationType, String valueText,
                                     String observedBy, String idempotencyKey) {
        if (transfusionEpisodeId == null) return false;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("observation_type", observationType);
            payload.put("value_text", valueText);
            payload.put("observed_by", observedBy);
            restTemplate.postForEntity(baseUrl + "/internal/v1/madi/transfusions/" + transfusionEpisodeId + "/observations",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            return true;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: MADI observation unavailable for episode {} (best-effort): {}",
                    transfusionEpisodeId, e.getMessage());
            return false;
        }
    }

    /** Complete a MADI transfusion episode with an outcome (COMPLETED | REACTION | ABANDONED). */
    public boolean completeTransfusion(String transfusionEpisodeId, String outcomeStatus, String outcomeNotes,
                                       String recordedBy, String idempotencyKey) {
        if (transfusionEpisodeId == null) return false;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("outcome_status", outcomeStatus != null ? outcomeStatus : "COMPLETED");
            payload.put("outcome_notes", outcomeNotes);
            payload.put("recorded_by", recordedBy);
            restTemplate.postForEntity(baseUrl + "/internal/v1/madi/transfusions/" + transfusionEpisodeId + "/complete",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            return true;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: MADI complete unavailable for episode {} (best-effort): {}",
                    transfusionEpisodeId, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractOrderId(Map<String, Object> body) {
        if (body == null) return null;
        Object data = body.containsKey("orderId") || body.containsKey("order_id") ? body : body.getOrDefault("data", body);
        if (data instanceof Map<?, ?> m) {
            Object id = firstNonNull(((Map<String, Object>) m).get("orderId"), ((Map<String, Object>) m).get("order_id"));
            return id != null ? id.toString() : null;
        }
        return null;
    }

    private static Object firstNonNull(Object a, Object b) { return a != null ? a : b; }

    private static String str(Object v) { return v != null ? v.toString() : null; }

    private HttpHeaders trustHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Mandatory companion quad — synthesise pod/request so peer v1.1 header enforcement passes.
        headers.set("X-Pod-ID", System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "inpatient");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey);
        try {
            TrustContext ctx = TrustContextHolder.require();
            if (ctx.tenantId() != null) headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            if (ctx.actorId() != null) headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            if (ctx.correlationId() != null) headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            else headers.set(TrustContext.H_CORRELATION_ID, UUID.randomUUID().toString());
            if (ctx.facilityId() != null) headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            if (ctx.purposeOfUse() != null) headers.set(TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse());
        } catch (IllegalStateException ignored) {
            headers.set(TrustContext.H_CORRELATION_ID, UUID.randomUUID().toString());
        }
        return headers;
    }
}
