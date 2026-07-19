package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;
import zw.gov.mohcc.impilo.experience.service.AppointmentCheckInService;
import zw.gov.mohcc.impilo.experience.service.SubjectResolutionService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Signed appointment token + self-check-in (Identity Journey Doctrine, CJ11). A citizen mints
 * a short-lived, cryptographically-signed check-in token for <b>their own</b> appointment
 * (rendered as a QR client-side), then presents it at the facility to self-check-in — no staff
 * keying of an appointment id, and no way to check in an appointment you don't hold a token for.
 *
 * <p>Reuses the governed Ed25519 scoped-token mechanism (tshepo-identity): the token binds the
 * intent + appointment id in its {@code scope} ({@code APPOINTMENT_CHECKIN:<id>}) targeted at
 * booking-service, with a short TTL, and is verified by introspection at check-in — so it is
 * revocable and cannot be replayed against another service or after expiry. Ownership is checked
 * server-side at mint time against the appointment's patient fields (Health ID or resolved CPID),
 * so a citizen can only mint a token for their own appointment.</p>
 */
@RestController
@RequestMapping("/internal/v1/appointments")
public class CitizenAppointmentCheckinController {

    private static final Logger log = LoggerFactory.getLogger(CitizenAppointmentCheckinController.class);

    private static final String SCOPE_PREFIX = "APPOINTMENT_CHECKIN:";
    private static final String TARGET = "booking-service";
    private static final int TOKEN_TTL_SECONDS = 7_200; // 2h window around the appointment

    private final BookingServiceClient booking;
    private final TshepoIdentityServiceClient tshepoIdentity;
    private final AppointmentCheckInService checkInService;
    private final SubjectResolutionService subjectResolution;

    public CitizenAppointmentCheckinController(BookingServiceClient booking,
                                               TshepoIdentityServiceClient tshepoIdentity,
                                               AppointmentCheckInService checkInService,
                                               SubjectResolutionService subjectResolution) {
        this.booking = booking;
        this.tshepoIdentity = tshepoIdentity;
        this.checkInService = checkInService;
        this.subjectResolution = subjectResolution;
    }

    /** Mint a signed check-in token for the caller's own appointment. */
    @GetMapping("/{appointmentId}/checkin-token")
    public ResponseEntity<Map<String, Object>> mintToken(
            @PathVariable String appointmentId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {

        if (actorId == null || actorId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "X-Actor-ID is required");
        }
        JsonNode appt = booking.getAppointment(appointmentId);
        if (appt == null || appt.isNull()) {
            return error(HttpStatus.NOT_FOUND, "Appointment not found");
        }
        if (!ownsAppointment(appt, actorId)) {
            // Never reveal whether the appointment exists for someone else — a flat 403.
            return error(HttpStatus.FORBIDDEN, "This appointment cannot be checked in from this account");
        }

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("tenantId", tenantId);
        req.put("actorId", actorId);
        req.put("purpose", "APPOINTMENT_CHECKIN");
        req.put("targetService", TARGET);
        req.put("scope", SCOPE_PREFIX + appointmentId);
        req.put("subjectRef", actorId);
        req.put("ttlSeconds", TOKEN_TTL_SECONDS);
        JsonNode token = tshepoIdentity.issueScopedToken(req);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("appointmentId", appointmentId);
        out.put("token", text(token, "token"));
        out.put("expiresAt", text(token, "expiresAt"));
        return ResponseEntity.ok(envelope(out, requestId, correlationId));
    }

    public record CheckinByTokenRequest(String token) {}

    /** Self-check-in by presenting a signed appointment token (scanned QR). */
    @PostMapping("/checkin-by-token")
    public ResponseEntity<Map<String, Object>> checkinByToken(
            @RequestBody CheckinByTokenRequest req,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {

        if (req == null || req.token() == null || req.token().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "A check-in token is required");
        }
        JsonNode intro;
        try {
            intro = tshepoIdentity.introspectScopedToken(Map.of("token", req.token()));
        } catch (Exception e) {
            log.debug("check-in token introspection failed: {}", e.getMessage());
            return error(HttpStatus.UNAUTHORIZED, "The check-in token is invalid or has expired");
        }
        if (intro == null || !intro.path("active").asBoolean(false)) {
            return error(HttpStatus.UNAUTHORIZED, "The check-in token is invalid or has expired");
        }
        String scope = text(intro, "scope");
        // Confirm this is an appointment-check-in token for booking-service — a token minted for
        // any other purpose/service can never be replayed here.
        if (scope == null || !scope.startsWith(SCOPE_PREFIX)
                || !TARGET.equals(text(intro, "targetService"))) {
            return error(HttpStatus.BAD_REQUEST, "This token is not a valid appointment check-in token");
        }
        String appointmentId = scope.substring(SCOPE_PREFIX.length());
        try {
            return checkInService.checkIn(UUID.fromString(appointmentId), requestId, correlationId);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "The check-in token references an invalid appointment");
        }
    }

    /** True when the appointment belongs to the caller (Health ID match, or resolved-CPID match). */
    private boolean ownsAppointment(JsonNode appt, String actorId) {
        if (matchesAny(actorId, appt, "patient_id", "patientId")) {
            return true;
        }
        String cpid = subjectResolution.cpidForHealthId(actorId);
        return cpid != null && matchesAny(cpid, appt, "patient_cpid", "patientCpid");
    }

    private static boolean matchesAny(String value, JsonNode node, String... fields) {
        for (String f : fields) {
            if (value.equals(text(node, f)) || value.equals(text(node.path("attributes"), f))) {
                return true;
            }
        }
        return false;
    }

    private static String text(JsonNode n, String field) {
        if (n == null) {
            return null;
        }
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", status.name(), "message", message)));
    }

    /** Wrap a success payload in the house {@code {data, meta}} envelope the shell expects. */
    private static Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("meta", meta);
        return body;
    }
}
