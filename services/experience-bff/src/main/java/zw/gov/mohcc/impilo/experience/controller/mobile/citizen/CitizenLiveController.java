package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

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
 * Citizen mobile surface for Impilo Live — discover, register, join, Q&A, feedback.
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/live")
public class CitizenLiveController {

    private static final Logger log = LoggerFactory.getLogger(CitizenLiveController.class);

    private final LiveServiceClient client;

    public CitizenLiveController(LiveServiceClient client) {
        this.client = client;
    }

    @GetMapping("/discover")
    public ResponseEntity<Map<String, Object>> discover(
            @RequestParam(value = "context_type", defaultValue = "CITIZEN") String contextType,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet(() -> client.discoverByContext(contextType), requestId, correlationId);
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
            payload.putIfAbsent("participantType", "CITIZEN");
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
            payload.putIfAbsent("participantType", "CITIZEN");
            payload.putIfAbsent("role", "ATTENDEE");
            return client.joinRoom(eventId, payload);
        }, requestId, correlationId);
    }

    @PostMapping("/events/{eventId}/questions")
    public ResponseEntity<Map<String, Object>> submitQuestion(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> {
            Map<String, Object> payload = new LinkedHashMap<>(body);
            payload.putIfAbsent("participantId", actorId);
            payload.putIfAbsent("participantType", "CITIZEN");
            return client.submitQuestion(eventId, payload);
        }, requestId, correlationId);
    }

    @PostMapping("/events/{eventId}/feedback")
    public ResponseEntity<Map<String, Object>> feedback(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> {
            Map<String, Object> payload = new LinkedHashMap<>(body);
            payload.putIfAbsent("participantId", actorId);
            return client.submitFeedback(eventId, payload);
        }, requestId, correlationId);
    }

    @FunctionalInterface
    private interface LiveCall {
        JsonNode call() throws Exception;
    }

    private ResponseEntity<Map<String, Object>> forwardGet(LiveCall call, String requestId, String correlationId) {
        try {
            return ResponseEntity.ok(envelope(call.call(), requestId, correlationId));
        } catch (Exception e) {
            log.warn("citizen live GET failed: {}", e.getMessage());
            return ResponseEntity.ok(envelope(Collections.emptyList(), requestId, correlationId));
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
