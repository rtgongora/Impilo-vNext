package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
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
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/resources/" + resourceId + "/bookings")
                .queryParam("from", from)
                .queryParam("to", to)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Cancel a booking.
     */
    public JsonNode cancelBooking(UUID bookingId, String reason) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/bookings/" + bookingId)
                .queryParam("reason", reason)
                .toUriString();
        log.info("TUSO: Cancelling booking={}, reason={}", bookingId, reason);
        restTemplate.delete(url);
        return null;
    }

    /**
     * List resources for a facility.
     */
    public JsonNode listFacilityResources(long facilityId, String resourceType) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/facilities/" + facilityId + "/resources");
        if (resourceType != null) builder.queryParam("resourceType", resourceType);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    // ── Shift Management ─────────────────────────────────────────────

    /** Get current active shift for a user. */
    public JsonNode getCurrentShift(String userId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/shifts/current")
                .queryParam("userId", userId)
                .toUriString();
        log.info("TUSO: Getting current shift for user={}", userId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Start a new shift. */
    public JsonNode startShift(Map<String, Object> body) {
        String url = baseUrl + "/v1/shifts/start";
        log.info("TUSO: Starting shift for user={}", body.get("user_id"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** End a shift. */
    public JsonNode endShift(String shiftId, Map<String, Object> body) {
        String url = baseUrl + "/v1/shifts/" + shiftId + "/end";
        log.info("TUSO: Ending shift={}", shiftId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Staffing ──────────────────────────────────────────────────────

    /** Get roster for a week. */
    public JsonNode getRosterWeek(String facilityId, String weekStart, String workspaceId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/staffing/roster-week")
                .queryParam("facilityId", facilityId)
                .queryParam("weekStart", weekStart);
        if (workspaceId != null && !workspaceId.isBlank()) builder.queryParam("workspaceId", workspaceId);
        log.info("TUSO: Getting roster week facilityId={}, weekStart={}", facilityId, weekStart);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /** List on-call assignments for a facility/week. */
    public JsonNode listOnCall(String facilityId, String weekStart) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/staffing/on-call")
                .queryParam("facilityId", facilityId)
                .queryParam("weekStart", weekStart)
                .toUriString();
        log.info("TUSO: Listing on-call facilityId={}, weekStart={}", facilityId, weekStart);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** List swap requests for a facility. */
    public JsonNode listSwapRequests(String facilityId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/staffing/on-call/swaps")
                .queryParam("facilityId", facilityId)
                .toUriString();
        log.info("TUSO: Listing swap requests facilityId={}", facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Create a swap request. */
    public JsonNode createSwapRequest(Map<String, Object> body) {
        String url = baseUrl + "/v1/staffing/on-call/swaps";
        log.info("TUSO: Creating swap request");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Update a swap request (approve/decline). */
    public JsonNode updateSwapRequest(String swapId, Map<String, Object> body) {
        String url = baseUrl + "/v1/staffing/on-call/swaps/" + swapId;
        log.info("TUSO: Updating swap request={}", swapId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Appointments/Scheduling ───────��───────────────────────────────

    /** List appointments with filters. */
    public JsonNode listAppointments(String patientId, String facilityId, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/appointments")
                .queryParam("page", page)
                .queryParam("size", size);
        if (patientId != null) builder.queryParam("patientId", patientId);
        if (facilityId != null) builder.queryParam("facilityId", facilityId);
        if (status != null) builder.queryParam("status", status);
        log.info("TUSO: Listing appointments");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /** Create an appointment. */
    public JsonNode createAppointment(Map<String, Object> body) {
        String url = baseUrl + "/v1/appointments";
        log.info("TUSO: Creating appointment");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Get a single appointment by ID. */
    public JsonNode getAppointment(String appointmentId) {
        String url = baseUrl + "/v1/appointments/" + appointmentId;
        log.info("TUSO: Getting appointment={}", appointmentId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Confirm an appointment. */
    public JsonNode confirmAppointment(String appointmentId) {
        String url = baseUrl + "/v1/appointments/" + appointmentId + "/confirm";
        log.info("TUSO: Confirming appointment={}", appointmentId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /** Cancel an appointment. */
    public JsonNode cancelAppointment(String appointmentId, String reason) {
        String url = baseUrl + "/v1/appointments/" + appointmentId + "/cancel";
        Map<String, Object> body = new LinkedHashMap<>();
        if (reason != null) body.put("reason", reason);
        log.info("TUSO: Cancelling appointment={}", appointmentId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Citizen Appointments ──────────────────────────────────────────

    /** List appointments for a citizen (patient CPID). */
    public JsonNode listCitizenAppointments(String cpid, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/appointments/citizen/" + cpid)
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null) builder.queryParam("status", status);
        log.info("TUSO: Listing citizen appointments for cpid={}", cpid);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /** Create a citizen appointment request. */
    public JsonNode createCitizenAppointment(String cpid, Map<String, Object> body) {
        String url = baseUrl + "/v1/appointments/citizen/" + cpid;
        log.info("TUSO: Creating citizen appointment for cpid={}", cpid);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Ward Management ───────────────────────────────────────────────

    /** Create a ward (delegates to TUSO facility registry). */
    public JsonNode createWard(Map<String, Object> body) {
        String url = baseUrl + "/v1/wards";
        log.info("TUSO: Creating ward name={}", body.get("name"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
