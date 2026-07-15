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
import java.util.Map;
import java.util.UUID;

/**
 * Best-effort client to PCT telemedicine (8088) so a deteriorating recovery patient can be linked to a
 * teleconsult session (tele-PACU / tele-ICU support, Wave 4 §6a). A PCT referral IS the teleconsult
 * session — session id = referral id. PCT is the SoR; inpatient records only the session ref.
 * Reuses the {@code inpatient.integration.pct.base-url} property (same as {@link TheatreDeathClient}).
 */
@Service
public class PctTeleconsultClient {

    private static final Logger log = LoggerFactory.getLogger(PctTeleconsultClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PctTeleconsultClient(RestTemplate inpatientRestTemplate,
                                @Value("${inpatient.integration.pct.base-url:http://localhost:8088}") String baseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
    }

    public record SessionView(String sessionId, String status, boolean available) {
        static SessionView unavailable() { return new SessionView(null, null, false); }
    }

    /**
     * Create a teleconsult session for postop support.
     *
     * @param urgency e.g. EMERGENCY (an emergency-advisory tele-PACU/ICU session).
     * @param purpose e.g. POSTOP_SUPPORT.
     */
    @SuppressWarnings("unchecked")
    public SessionView createSession(String patientCpid, String encounterRef, String specialty,
                                     String urgency, String purpose, String clinicalQuestion, String idempotencyKey) {
        if (patientCpid == null || patientCpid.isBlank()) return SessionView.unavailable();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("patient_id", patientCpid);
            if (encounterRef != null) payload.put("encounter_id", encounterRef);
            payload.put("specialty", specialty != null ? specialty : "ANAESTHESIA");
            payload.put("urgency", urgency != null ? urgency : "EMERGENCY");
            payload.put("purpose_of_use", purpose != null ? purpose : "POSTOP_SUPPORT");
            payload.put("virtual_mode", "video");
            payload.put("modality", "virtual");
            payload.put("consent_required", true);
            payload.put("reason", clinicalQuestion != null ? clinicalQuestion : "Post-operative recovery support");
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/v1/referrals",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            SessionView view = toView(resp.getBody());
            if (view.sessionId() != null) {
                log.info("INPATIENT-THEATRE: created PCT teleconsult session {} (purpose {})", view.sessionId(), purpose);
            }
            return view;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: PCT teleconsult unavailable (best-effort): {}", e.getMessage());
            return SessionView.unavailable();
        }
    }

    @SuppressWarnings("unchecked")
    private static SessionView toView(Map<String, Object> body) {
        if (body == null) return SessionView.unavailable();
        Object data = body.getOrDefault("data", body);
        if (!(data instanceof Map<?, ?> m)) return SessionView.unavailable();
        Map<String, Object> dm = (Map<String, Object>) m;
        Object id = dm.getOrDefault("id", dm.getOrDefault("referral_id", dm.get("referralId")));
        Object status = dm.get("status");
        return new SessionView(id != null ? id.toString() : null,
                status != null ? status.toString() : null, id != null);
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
