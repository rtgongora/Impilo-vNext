package zw.gov.mohcc.impilo.scheduling.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Calls booking-service to create the THEATRE booking that funnels through
 * BookingIntegrationService.ensureProcedureEpisode() → the ONE case source of
 * truth (inpatient.procedure_episode). Scheduling never creates the episode
 * itself — it publishes a booking and reads back the episode reference.
 */
@Component
public class BookingClient {

    private static final Logger log = LoggerFactory.getLogger(BookingClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public BookingClient(@Value("${scheduling.integration.booking-base-url:http://localhost:8265}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Creates a THEATRE booking. Booking approval funnels to the episode seam.
     * Returns the booking id (and episode ref when the downstream surfaces it).
     */
    public Optional<BookingResult> createTheatreBooking(String patientId, String serviceName,
                                                        String surgeonRef, UUID clinicalSpaceId,
                                                        String preferredStartAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", patientId);
        body.put("booking_type", "THEATRE");
        body.put("service_name", serviceName != null ? serviceName : "Elective theatre procedure");
        if (surgeonRef != null) {
            body.put("provider_id", surgeonRef);
        }
        if (clinicalSpaceId != null) {
            body.put("resource_id", clinicalSpaceId.toString());
        }
        if (preferredStartAt != null) {
            body.put("preferred_start_at", preferredStartAt);
        }
        // Elective theatre cases are already governed through waitlist + conflict
        // detection; the booking auto-confirms so it funnels straight to
        // ensureProcedureEpisode (the ONE case source of truth).
        body.put("requires_approval", false);
        try {
            HttpHeaders headers = trustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    baseUrl + "/v1/bookings", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode root = resp.getBody();
            if (root == null) {
                return Optional.empty();
            }
            JsonNode data = root.has("data") ? root.get("data") : root;
            String bookingId = data.has("id") ? data.get("id").asText()
                    : (data.has("booking_id") ? data.get("booking_id").asText() : null);
            String episodeRef = data.has("procedure_episode_ref")
                    ? data.get("procedure_episode_ref").asText() : null;
            return Optional.of(new BookingResult(bookingId, episodeRef));
        } catch (Exception e) {
            log.warn("Booking creation failed for patient {}: {}", patientId, e.getMessage());
            return Optional.empty();
        }
    }

    private HttpHeaders trustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            if (ctx.tenantId() != null) {
                headers.set("X-Tenant-ID", ctx.tenantId().toString());
            }
            if (ctx.actorId() != null) {
                headers.set("X-Actor-ID", ctx.actorId());
            }
            if (ctx.actorType() != null) {
                headers.set("X-Actor-Type", ctx.actorType());
            }
        } catch (RuntimeException ignored) {
            // service-originated call without a request context — set service headers
        }
        headers.set("X-Pod-ID", "national-spine");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        headers.set("X-Correlation-ID", UUID.randomUUID().toString());
        headers.set("X-Access-Mode", "INTERNAL");
        headers.set("X-Service-Id", "scheduling-service");
        return headers;
    }

    public record BookingResult(String bookingId, String episodeRef) {
    }
}
