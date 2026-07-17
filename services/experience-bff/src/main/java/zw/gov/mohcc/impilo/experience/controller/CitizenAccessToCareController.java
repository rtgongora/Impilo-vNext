package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.service.CitizenAccessToCareService;

import java.util.Map;

/**
 * Authed citizen access-to-care actions for the find-care journey — the resource-moving steps a
 * signed-in person takes after the anonymous find-care search (appointment request, planned patient
 * transport, referral movement status).
 *
 * <p>These are NOT on the anonymous public gateway lane: booking and transport move real resources,
 * so they require a person anchor (doctrine: "trust rises with the action; care before coverage").
 * The public find-care UI routes a "Book" / "Request transport" tap to sign-in with a {@code returnTo}
 * that lands back here. Security: {@code /internal/v1/citizen/access-to-care/**} is CITIZEN-role
 * gated in {@code SecurityConfig}; abuse controls, ownership binding, and PII-safe shaping live in
 * {@link CitizenAccessToCareService}. The controller never fabricates a slot, confirmation, or
 * dispatch — it returns the sovereign source's honest reference + pending status.</p>
 */
@RestController
@RequestMapping("/internal/v1/citizen/access-to-care")
public class CitizenAccessToCareController {

    private static final Logger log = LoggerFactory.getLogger(CitizenAccessToCareController.class);

    private final CitizenAccessToCareService service;

    public CitizenAccessToCareController(CitizenAccessToCareService service) {
        this.service = service;
    }

    public record AppointmentRequestBody(
            @NotBlank String facilityId,
            @NotBlank String service,
            @NotBlank String preferredDate,
            String reason) {}

    /** Governed appointment request over booking-service (persists REQUESTED; no slot fabricated). */
    @PostMapping("/appointments/request")
    public ResponseEntity<?> requestAppointment(
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody AppointmentRequestBody body) {
        try {
            Map<String, Object> receipt = service.requestAppointment(
                    actorId, body.facilityId(), body.service(), body.preferredDate(), body.reason());
            return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
        } catch (CitizenAccessToCareService.AccessToCareValidationException e) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
        } catch (Exception e) {
            log.warn("Citizen appointment request failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "APPOINTMENT_UNAVAILABLE",
                    "We couldn't send your appointment request just now. Please try again shortly.");
        }
    }

    /** Planned patient-transport request over nhume (PATIENT). Emergency uses the SOS lane instead. */
    @PostMapping("/transport/request")
    public ResponseEntity<?> requestTransport(
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            Map<String, Object> receipt = service.requestTransport(actorId, body != null ? body : Map.of());
            return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
        } catch (CitizenAccessToCareService.AccessToCareValidationException e) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
        } catch (CitizenAccessToCareService.AccessToCareRateLimitedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                    .body(Map.of("error", Map.of("code", "RATE_LIMITED", "message", e.getMessage())));
        } catch (Exception e) {
            log.warn("Citizen transport request failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "TRANSPORT_UNAVAILABLE",
                    "We couldn't send your transport request just now. Please try again shortly.");
        }
    }

    /** Citizen-safe transport status by the transport reference (ownership-verified). */
    @GetMapping("/transport/{transportRef}")
    public ResponseEntity<?> transportStatus(
            @RequestHeader("X-Actor-ID") String actorId,
            @PathVariable String transportRef) {
        try {
            return ResponseEntity.ok(service.transportStatus(actorId, transportRef));
        } catch (CitizenAccessToCareService.AccessToCareNotFoundException e) {
            return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
        } catch (Exception e) {
            log.warn("Citizen transport status read failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "TRANSPORT_UNAVAILABLE",
                    "We couldn't read your transport status just now. Please try again shortly.");
        }
    }

    /** Citizen-safe referral movement summary (+ optional transport leg via {@code transportRef}). */
    @GetMapping("/referrals/{referralId}/movement")
    public ResponseEntity<?> referralMovement(
            @RequestHeader("X-Actor-ID") String actorId,
            @PathVariable String referralId,
            @RequestParam(required = false) String transportRef) {
        try {
            return ResponseEntity.ok(service.referralMovement(actorId, referralId, transportRef));
        } catch (CitizenAccessToCareService.AccessToCareNotFoundException e) {
            return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
        } catch (Exception e) {
            log.warn("Citizen referral movement read failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "REFERRAL_UNAVAILABLE",
                    "We couldn't read your referral status just now. Please try again shortly.");
        }
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("error", Map.of("code", code, "message", message)));
    }
}
