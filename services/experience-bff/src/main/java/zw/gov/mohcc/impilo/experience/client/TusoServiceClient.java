package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the TUSO (Facility Registry) sovereign service.
 *
 * <p>Provides access to facility resources, bookings (scheduling),
 * and shift management. TUSO manages the canonical booking lifecycle
 * with conflict detection and resource calendar management.</p>
 */
@Component
public class TusoServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TusoServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TusoServiceClient(RestTemplate serviceRestTemplate,
                             ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.tusoBaseUrl();
    }

    /**
     * Create a booking for a facility resource.
     *
     * @param resourceId the TUSO resource UUID
     * @param subjectRef the patient/subject reference (e.g. CPID)
     * @param purpose    booking purpose (e.g. "OPD Consultation")
     * @param startTime  booking start
     * @param endTime    booking end
     * @param notes      optional notes
     * @return the booking response from TUSO
     */
    public JsonNode createBooking(UUID resourceId, String subjectRef, String purpose,
                                  OffsetDateTime startTime, OffsetDateTime endTime, String notes) {
        String url = baseUrl + "/v1/resources/" + resourceId + "/bookings";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subjectRef", subjectRef);
        body.put("purpose", purpose);
        body.put("startTime", startTime.toString());
        body.put("endTime", endTime.toString());
        if (notes != null) body.put("notes", notes);

        log.info("TUSO: Creating booking for resource={}, subject={}, start={}",
                resourceId, subjectRef, startTime);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * List bookings for a resource in a time range.
     */
    public JsonNode listBookings(UUID resourceId, OffsetDateTime from, OffsetDateTime to) {
        String url = baseUrl + "/v1/resources/" + resourceId + "/bookings?from=" + from + "&to=" + to;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Cancel a booking.
     */
    public JsonNode cancelBooking(UUID bookingId, String reason) {
        String url = baseUrl + "/v1/bookings/" + bookingId + "?reason=" + reason;
        log.info("TUSO: Cancelling booking={}, reason={}", bookingId, reason);
        restTemplate.delete(url);
        return null;
    }

    /**
     * List resources for a facility.
     */
    public JsonNode listFacilityResources(long facilityId, String resourceType) {
        StringBuilder url = new StringBuilder(baseUrl + "/v1/facilities/" + facilityId + "/resources");
        if (resourceType != null) url.append("?resourceType=").append(resourceType);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
