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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Best-effort client to booking-service (8265) so a surgical discharge follow-up becomes a REAL booking
 * (FOLLOW_UP or TELECONSULT) rather than a free-text note. Booking-service is the SoR for the appointment;
 * inpatient only records the returned booking ref. Mirrors {@link NhumeTransportClient} (full trust
 * headers + idempotency). Never fabricates a booking: an outage returns null.
 */
@Service
public class BookingFollowUpClient {

    private static final Logger log = LoggerFactory.getLogger(BookingFollowUpClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BookingFollowUpClient(RestTemplate inpatientRestTemplate,
                                 @Value("${inpatient.integration.booking.base-url:http://localhost:8265}") String baseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Create a discharge follow-up booking.
     *
     * @param teleconsult when true, books a TELECONSULT (video); otherwise an in-person FOLLOW_UP.
     * @return the booking id (String), or null on outage / rejection.
     */
    @SuppressWarnings("unchecked")
    public String createFollowUp(String clientCpid, String serviceName, LocalDate when, boolean teleconsult,
                                 String reason, String idempotencyKey) {
        if (clientCpid == null || clientCpid.isBlank()) return null;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("client_id", clientCpid);
            payload.put("booking_type", teleconsult ? "TELECONSULT" : "FOLLOW_UP");
            payload.put("service_name", serviceName != null ? serviceName : "Post-operative review");
            payload.put("preferred_channel", teleconsult ? "VIDEO" : "IN_PERSON");
            if (when != null) payload.put("preferred_start_at", when.atStartOfDay().toString());
            payload.put("clinical_priority", "ROUTINE");
            payload.put("reason", reason != null ? reason : "Post-operative follow-up");
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/v1/bookings",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            String id = extractId(resp.getBody());
            if (id != null) {
                log.info("INPATIENT-DISCHARGE: created follow-up booking {} (teleconsult={})", id, teleconsult);
            }
            return id;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-DISCHARGE: booking-service unavailable creating follow-up (best-effort): {}",
                    e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractId(Map<String, Object> body) {
        if (body == null) return null;
        Object data = body.getOrDefault("data", body);
        if (!(data instanceof Map<?, ?> m)) return null;
        Object id = ((Map<String, Object>) m).getOrDefault("id", ((Map<String, Object>) m).get("booking_number"));
        return id != null ? id.toString() : null;
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
