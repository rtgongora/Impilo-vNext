package zw.gov.mohcc.impilo.experience.controller.mobile.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.LiveServiceClient;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider mobile surface for Impilo Live — discover, register, join, host, CPD, certificates.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/live")
public class ProviderLiveController {

    private static final Logger log = LoggerFactory.getLogger(ProviderLiveController.class);

    private final LiveServiceClient client;

    public ProviderLiveController(LiveServiceClient client) {
        this.client = client;
    }

    @GetMapping("/discover")
    public ResponseEntity<Map<String, Object>> discover(
            @RequestParam(value = "facility_id", required = false) String facilityId,
            @RequestParam(value = "context_type", defaultValue = "PROFESSIONAL") String contextType,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestParam(value = "role", defaultValue = "PROVIDER") String role,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data;
            if (facilityId != null && !facilityId.isBlank()) {
                data = client.discoverByFacility(facilityId);
            } else if (actorId != null && !actorId.isBlank()) {
                data = client.discoverByRole(actorId, role);
            } else {
                data = client.discoverByContext(contextType);
            }
            return ResponseEntity.ok(envelope(data, requestId, correlationId));
        } catch (Exception e) {
            log.warn("provider live discover failed: {}", e.getMessage());
            return ResponseEntity.ok(envelope(Collections.emptyList(), requestId, correlationId));
        }
    }

    @PostMapping("/events/{eventId}/register")
    public ResponseEntity<Map<String, Object>> register(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> {
            Map<String, Object> payload = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
            payload.putIfAbsent("participantId", actorId);
            payload.putIfAbsent("participantType", "PROVIDER");
            return client.register(eventId, payload);
        }, requestId, correlationId);
    }

    @PostMapping("/events/{eventId}/join")
    public ResponseEntity<Map<String, Object>> join(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> {
            Map<String, Object> payload = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
            payload.putIfAbsent("participantId", actorId);
            payload.putIfAbsent("participantType", "PROVIDER");
            payload.putIfAbsent("role", "ATTENDEE");
            return client.joinRoom(eventId, payload);
        }, requestId, correlationId);
    }

    @PostMapping("/events/{eventId}/start")
    public ResponseEntity<Map<String, Object>> startEvent(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> client.startRoom(eventId), requestId, correlationId);
    }

    @PostMapping("/events/{eventId}/end")
    public ResponseEntity<Map<String, Object>> endEvent(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> client.endRoom(eventId), requestId, correlationId);
    }

    @PostMapping("/events/{eventId}/token")
    public ResponseEntity<Map<String, Object>> hostToken(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> {
            Map<String, Object> payload = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
            payload.putIfAbsent("participantId", actorId);
            payload.putIfAbsent("role", "HOST");
            return client.roomToken(eventId, payload);
        }, requestId, correlationId);
    }

    @GetMapping("/events/{eventId}/attendance")
    public ResponseEntity<Map<String, Object>> myAttendance(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> client.getAttendance(eventId, actorId), requestId, correlationId);
    }

    @PostMapping("/events/{eventId}/cpd")
    public ResponseEntity<Map<String, Object>> issueCpd(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.issueCpdCertificate(eventId, actorId), requestId, correlationId);
    }

    @GetMapping("/events/{eventId}/certificates")
    public ResponseEntity<Map<String, Object>> listCertificates(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> client.listCertificates(eventId), requestId, correlationId);
    }

    @PostMapping("/events/{eventId}/certificates/attendance")
    public ResponseEntity<Map<String, Object>> issueAttendanceCertificate(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.issueAttendanceCertificate(eventId, actorId), requestId, correlationId);
    }

    @GetMapping("/certificates/verify/{verificationCode}")
    public ResponseEntity<Map<String, Object>> verifyCertificate(
            @PathVariable String verificationCode,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> client.verifyCertificate(verificationCode), requestId, correlationId);
    }

    @FunctionalInterface
    private interface LiveCall {
        JsonNode call() throws Exception;
    }

    private ResponseEntity<Map<String, Object>> forward(LiveCall call, String requestId, String correlationId) {
        try {
            return ResponseEntity.ok(envelope(call.call(), requestId, correlationId));
        } catch (Exception e) {
            return upstreamFailure(requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> forwardPost(LiveCall call, String requestId, String correlationId) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(call.call(), requestId, correlationId));
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
        err.put("error", Map.of("code", "LIVE_UNAVAILABLE", "message", "Live events service is temporarily unavailable."));
        err.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(err);
    }
}
