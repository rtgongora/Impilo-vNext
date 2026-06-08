package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP client for the booking-service sovereign Booking + Appointment aggregates.
 */
@Component
public class BookingServiceClient {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BookingServiceClient(RestTemplate serviceRestTemplate,
                                ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.bookingBaseUrl();
    }

    // ── Bookings ──────────────────────────────────────────────────────

    public JsonNode listBookings(String clientId, String facilityId, String providerId,
                                 String bookingStatus, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/bookings")
                .queryParam("page", page)
                .queryParam("size", size);
        if (clientId != null && !clientId.isBlank()) builder.queryParam("clientId", clientId);
        if (facilityId != null && !facilityId.isBlank()) builder.queryParam("facilityId", facilityId);
        if (providerId != null && !providerId.isBlank()) builder.queryParam("providerId", providerId);
        if (bookingStatus != null && !bookingStatus.isBlank()) builder.queryParam("bookingStatus", bookingStatus);
        log.info("Booking: list bookings");
        return extractData(restTemplate.getForEntity(builder.toUriString(), JsonNode.class));
    }

    public JsonNode getBooking(String bookingId) {
        String url = baseUrl + "/v1/bookings/" + bookingId;
        log.info("Booking: get booking={}", bookingId);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createBooking(Map<String, Object> body) {
        String url = baseUrl + "/v1/bookings";
        log.info("Booking: create booking");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode updateBooking(String bookingId, Map<String, Object> body) {
        String url = baseUrl + "/v1/bookings/" + bookingId;
        log.info("Booking: update booking={}", bookingId);
        return extractData(restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(body), JsonNode.class));
    }

    public JsonNode cancelBooking(String bookingId, String reason) {
        String url = baseUrl + "/v1/bookings/" + bookingId + "/cancel";
        Map<String, Object> body = new LinkedHashMap<>();
        if (reason != null) body.put("reason", reason);
        log.info("Booking: cancel booking={}", bookingId);
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode triageBooking(String bookingId, Map<String, Object> body) {
        String url = baseUrl + "/v1/bookings/" + bookingId + "/triage";
        log.info("Booking: triage booking={}", bookingId);
        return extractData(restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class));
    }

    public JsonNode approveBooking(String bookingId) {
        String url = baseUrl + "/v1/bookings/" + bookingId + "/approve";
        log.info("Booking: approve booking={}", bookingId);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode rejectBooking(String bookingId, String reason) {
        String url = baseUrl + "/v1/bookings/" + bookingId + "/reject";
        Map<String, Object> body = new LinkedHashMap<>();
        if (reason != null) body.put("reason", reason);
        log.info("Booking: reject booking={}", bookingId);
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode assignBooking(String bookingId, Map<String, Object> body) {
        String url = baseUrl + "/v1/bookings/" + bookingId + "/assign";
        log.info("Booking: assign booking={}", bookingId);
        return extractData(restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class));
    }

    public JsonNode reserveBooking(String bookingId) {
        String url = baseUrl + "/v1/bookings/" + bookingId + "/reserve";
        log.info("Booking: reserve booking={}", bookingId);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode convertBookingToAppointment(String bookingId) {
        String url = baseUrl + "/v1/bookings/" + bookingId + "/convert";
        log.info("Booking: convert booking={} to appointment", bookingId);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode checkAvailability(String targetType, String targetRef, String facilityId, String date) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/bookings/availability")
                .queryParam("targetType", targetType)
                .queryParam("targetRef", targetRef)
                .queryParam("date", date);
        if (facilityId != null && !facilityId.isBlank()) builder.queryParam("facilityId", facilityId);
        log.info("Booking: check availability targetType={} date={}", targetType, date);
        return extractData(restTemplate.getForEntity(builder.toUriString(), JsonNode.class));
    }

    public JsonNode getMvumoStatus(String bookingId) {
        String url = baseUrl + "/v1/bookings/" + bookingId + "/mvumo/status";
        log.info("Booking: mvumo status booking={}", bookingId);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode requestMvumo(String bookingId, Map<String, Object> body) {
        String url = baseUrl + "/v1/bookings/" + bookingId + "/mvumo/request";
        log.info("Booking: mvumo request booking={}", bookingId);
        return extractData(restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class));
    }

    // ── Appointments ──────────────────────────────────────────────────

    public JsonNode listAppointments(String clientId, String facilityId, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/appointments")
                .queryParam("page", page)
                .queryParam("size", size);
        if (clientId != null && !clientId.isBlank()) builder.queryParam("clientId", clientId);
        if (facilityId != null && !facilityId.isBlank()) builder.queryParam("facilityId", facilityId);
        if (status != null && !status.isBlank()) builder.queryParam("status", status);
        log.info("Booking: list appointments");
        return extractData(restTemplate.getForEntity(builder.toUriString(), JsonNode.class));
    }

    public JsonNode getAppointment(String appointmentId) {
        String url = baseUrl + "/v1/appointments/" + appointmentId;
        log.info("Booking: get appointment={}", appointmentId);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createAppointment(Map<String, Object> body) {
        String url = baseUrl + "/v1/appointments";
        log.info("Booking: create appointment");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode confirmAppointment(String appointmentId) {
        String url = baseUrl + "/v1/appointments/" + appointmentId + "/confirm";
        log.info("Booking: confirm appointment={}", appointmentId);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode cancelAppointment(String appointmentId, String reason) {
        String url = baseUrl + "/v1/appointments/" + appointmentId + "/cancel";
        Map<String, Object> body = new LinkedHashMap<>();
        if (reason != null) body.put("reason", reason);
        log.info("Booking: cancel appointment={}", appointmentId);
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode checkInAppointment(String appointmentId) {
        return checkInAppointment(appointmentId, null);
    }

    public JsonNode checkInAppointment(String appointmentId, String queueId) {
        String url = baseUrl + "/v1/appointments/" + appointmentId + "/check-in";
        if (queueId != null && !queueId.isBlank()) {
            url += "?queueId=" + queueId;
        }
        log.info("Booking: check-in appointment={} queueId={}", appointmentId, queueId);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode completeAppointment(String appointmentId) {
        String url = baseUrl + "/v1/appointments/" + appointmentId + "/complete";
        log.info("Booking: complete appointment={}", appointmentId);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode noShowAppointment(String appointmentId) {
        String url = baseUrl + "/v1/appointments/" + appointmentId + "/no-show";
        log.info("Booking: no-show appointment={}", appointmentId);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode rescheduleAppointment(String appointmentId, Map<String, Object> body) {
        String url = baseUrl + "/v1/appointments/" + appointmentId + "/reschedule";
        log.info("Booking: reschedule appointment={}", appointmentId);
        return extractData(restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class));
    }

    // ── Citizen ───────────────────────────────────────────────────────

    public JsonNode listCitizenAppointments(String cpid, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/appointments/citizen/" + cpid)
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null && !status.isBlank()) builder.queryParam("status", status);
        log.info("Booking: list citizen appointments cpid={}", cpid);
        return extractData(restTemplate.getForEntity(builder.toUriString(), JsonNode.class));
    }

    /**
     * Citizen self-service creates a booking in REQUESTED state (not a direct appointment).
     */
    public JsonNode createCitizenBooking(String clientId, Map<String, Object> body) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("client_id", clientId);
        Object facilityId = body.getOrDefault("facilityId", body.get("facility_id"));
        if (facilityId != null) request.put("facility_id", facilityId);
        Object bookingType = body.getOrDefault("bookingType", body.getOrDefault("appointmentType", body.get("booking_type")));
        if (bookingType != null) request.put("booking_type", bookingType);
        Object preferred = body.getOrDefault("preferredStartTime",
                body.getOrDefault("preferredDate", body.get("preferred_start_at")));
        if (preferred != null) request.put("preferred_start_at", preferred);
        Object reason = body.getOrDefault("reasonForBooking", body.get("reason"));
        if (reason != null) request.put("reason", reason);
        if (body.containsKey("providerId") || body.containsKey("provider_id")) {
            request.put("provider_id", body.getOrDefault("providerId", body.get("provider_id")));
        }
        if (body.containsKey("targetType") || body.containsKey("target_type")) {
            request.put("target_type", body.getOrDefault("targetType", body.get("target_type")));
        }
        if (body.containsKey("targetRef") || body.containsKey("target_ref")) {
            request.put("target_ref", body.getOrDefault("targetRef", body.get("target_ref")));
        }
        log.info("Booking: create citizen booking clientId={}", clientId);
        return createBooking(request);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
