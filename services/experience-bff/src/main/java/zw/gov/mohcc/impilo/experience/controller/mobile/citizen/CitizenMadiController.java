package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MadiServiceClient;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Citizen mobile surface for MADI donor self-service.
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/madi")
public class CitizenMadiController {

    private static final Logger log = LoggerFactory.getLogger(CitizenMadiController.class);

    private final MadiServiceClient client;

    public CitizenMadiController(MadiServiceClient client) {
        this.client = client;
    }

    /** Session-scoped donor registration (mobile client uses X-Actor-ID as person CPID). */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerSession(
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        body.put("person_cpid", actorId);
        return forwardPost(() -> client.registerDonor(body), requestId, correlationId);
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> profileSession(
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet(() -> client.donorByPerson(actorId), requestId, correlationId);
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> historySession(
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet(() -> client.donorHistory(requireDonorId(actorId)), requestId, correlationId);
    }

    @GetMapping("/next-eligibility")
    public ResponseEntity<Map<String, Object>> nextEligibilitySession(
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet(() -> client.donorNextEligibility(requireDonorId(actorId)), requestId, correlationId);
    }

    @PutMapping("/preferences")
    public ResponseEntity<Map<String, Object>> preferencesSession(
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPut(() -> client.donorPreferences(requireDonorId(actorId), body), requestId, correlationId);
    }

    @PostMapping("/feedback")
    public ResponseEntity<Map<String, Object>> feedbackSession(
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.donorFeedback(requireDonorId(actorId), body), requestId, correlationId);
    }

    @PostMapping("/pre-screening")
    public ResponseEntity<Map<String, Object>> preScreeningSession(
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.submitPreScreening(requireDonorId(actorId), body), requestId, correlationId);
    }

    @GetMapping("/pre-screening/latest")
    public ResponseEntity<Map<String, Object>> latestPreScreeningSession(
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet(() -> client.latestPreScreening(requireDonorId(actorId)), requestId, correlationId);
    }

    @PostMapping("/screenings")
    public ResponseEntity<Map<String, Object>> screeningSession(
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.screenDonor(requireDonorId(actorId), body), requestId, correlationId);
    }

    @PostMapping("/drives/{driveId}/register")
    public ResponseEntity<Map<String, Object>> registerForDriveSession(
            @PathVariable String driveId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> {
            Map<String, Object> payload = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
            payload.putIfAbsent("donor_id", requireDonorId(actorId));
            return client.registerAtDrive(driveId, payload);
        }, requestId, correlationId);
    }

    @PostMapping("/donors/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.registerDonor(body), requestId, correlationId);
    }

    @GetMapping("/donors/profile")
    public ResponseEntity<Map<String, Object>> profile(
            @RequestParam("person_cpid") String personCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet(() -> client.donorByPerson(personCpid), requestId, correlationId);
    }

    @PostMapping("/donors/{donorId}/screening")
    public ResponseEntity<Map<String, Object>> screening(
            @PathVariable String donorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.screenDonor(donorId, body), requestId, correlationId);
    }

    @GetMapping("/drives/near-me")
    public ResponseEntity<Map<String, Object>> drivesNearMe(
            @RequestParam("ndila_site_ref") String ndilaSiteRef,
            @RequestParam(value = "radius_km", defaultValue = "25") double radiusKm,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet(() -> client.drivesNearMe(ndilaSiteRef, radiusKm), requestId, correlationId);
    }

    @PostMapping("/donors/{donorId}/feedback")
    public ResponseEntity<Map<String, Object>> feedback(
            @PathVariable String donorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.donorFeedback(donorId, body), requestId, correlationId);
    }

    @PutMapping("/donors/{donorId}/preferences")
    public ResponseEntity<Map<String, Object>> preferences(
            @PathVariable String donorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPut(() -> client.donorPreferences(donorId, body), requestId, correlationId);
    }

    @GetMapping("/donors/{donorId}/history")
    public ResponseEntity<Map<String, Object>> history(
            @PathVariable String donorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet(() -> client.donorHistory(donorId), requestId, correlationId);
    }

    @GetMapping("/donors/{donorId}/next-eligibility")
    public ResponseEntity<Map<String, Object>> nextEligibility(
            @PathVariable String donorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet(() -> client.donorNextEligibility(donorId), requestId, correlationId);
    }

    @PostMapping("/donors/{donorId}/pre-screening")
    public ResponseEntity<Map<String, Object>> preScreening(
            @PathVariable String donorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.submitPreScreening(donorId, body), requestId, correlationId);
    }

    @GetMapping("/donors/{donorId}/pre-screening/latest")
    public ResponseEntity<Map<String, Object>> latestPreScreening(
            @PathVariable String donorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet(() -> client.latestPreScreening(donorId), requestId, correlationId);
    }

    private String requireDonorId(String personCpid) throws Exception {
        JsonNode donor = client.donorByPerson(personCpid);
        if (donor == null || donor.isNull() || donor.isMissingNode()) {
            throw new IllegalStateException("Donor profile not found for signed-in person");
        }
        if (donor.has("donorId")) {
            return donor.get("donorId").asText();
        }
        if (donor.has("donor_id")) {
            return donor.get("donor_id").asText();
        }
        throw new IllegalStateException("Donor profile missing donor identifier");
    }

    @FunctionalInterface
    private interface MadiCall {
        JsonNode call() throws Exception;
    }

    private ResponseEntity<Map<String, Object>> forwardGet(MadiCall call, String requestId, String correlationId) {
        try {
            return ResponseEntity.ok(envelope(call.call(), requestId, correlationId));
        } catch (Exception e) {
            log.warn("citizen madi GET failed: {}", e.getMessage());
            return ResponseEntity.ok(envelope(Collections.emptyList(), requestId, correlationId));
        }
    }

    private ResponseEntity<Map<String, Object>> forwardPost(MadiCall call, String requestId, String correlationId) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(call.call(), requestId, correlationId));
        } catch (Exception e) {
            return upstreamFailure(requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> forwardPut(MadiCall call, String requestId, String correlationId) {
        try {
            return ResponseEntity.ok(envelope(call.call(), requestId, correlationId));
        } catch (Exception e) {
            return upstreamFailure(requestId, correlationId);
        }
    }

    private static Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", data != null ? data : Collections.emptyList());
        envelope.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return envelope;
    }

    private static ResponseEntity<Map<String, Object>> upstreamFailure(String requestId, String correlationId) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", Map.of("code", "MADI_UNAVAILABLE", "message", "Blood donation service is temporarily unavailable."));
        err.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(err);
    }
}
