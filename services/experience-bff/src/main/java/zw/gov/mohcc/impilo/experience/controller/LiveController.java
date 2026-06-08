package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import zw.gov.mohcc.impilo.experience.client.LiveServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Experience BFF proxy for live-service ({@code /internal/v1/live/**}).
 */
@RestController
@RequestMapping("/internal/v1/live")
public class LiveController {

    private static final Logger log = LoggerFactory.getLogger(LiveController.class);

    private final LiveServiceClient client;

    public LiveController(LiveServiceClient client) {
        this.client = client;
    }

    // ── Events ───────────────────────────────────────────────────────

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> createEvent(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.createEvent(body), requestId, correlationId, true);
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<Map<String, Object>> getEvent(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.getEvent(eventId), requestId, correlationId, false);
    }

    @PutMapping("/events/{eventId}")
    public ResponseEntity<Map<String, Object>> updateEvent(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.updateEvent(eventId, body), requestId, correlationId, false);
    }

    @PostMapping("/events/{eventId}/schedule")
    public ResponseEntity<Map<String, Object>> scheduleEvent(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.scheduleEvent(eventId), requestId, correlationId, false);
    }

    @PostMapping("/events/{eventId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelEvent(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.cancelEvent(eventId), requestId, correlationId, false);
    }

    @GetMapping("/events")
    public ResponseEntity<Map<String, Object>> listEvents(
            @RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listEvents(status), requestId, correlationId, false);
    }

    // ── Registrations ────────────────────────────────────────────────

    @PostMapping("/registrations/events/{eventId}")
    public ResponseEntity<Map<String, Object>> register(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.register(eventId, body), requestId, correlationId, true);
    }

    @DeleteMapping("/registrations/events/{eventId}/participants/{participantId}")
    public ResponseEntity<Map<String, Object>> cancelRegistration(
            @PathVariable String eventId,
            @PathVariable String participantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.cancelRegistration(eventId, participantId), requestId, correlationId, false);
    }

    @PutMapping("/registrations/events/{eventId}/participants/{participantId}/saved")
    public ResponseEntity<Map<String, Object>> saveRegistration(
            @PathVariable String eventId,
            @PathVariable String participantId,
            @RequestParam boolean saved,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.saveRegistration(eventId, participantId, saved), requestId, correlationId, false);
    }

    @GetMapping("/registrations/events/{eventId}")
    public ResponseEntity<Map<String, Object>> listRegistrationsForEvent(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listRegistrationsForEvent(eventId), requestId, correlationId, false);
    }

    @GetMapping("/registrations/participants/{participantId}")
    public ResponseEntity<Map<String, Object>> listRegistrationsForParticipant(
            @PathVariable String participantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listRegistrationsForParticipant(participantId), requestId, correlationId, false);
    }

    // ── Room ─────────────────────────────────────────────────────────

    @PostMapping("/room/{eventId}/join")
    public ResponseEntity<Map<String, Object>> joinRoom(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.joinRoom(eventId, body), requestId, correlationId, true);
    }

    @PostMapping("/room/{eventId}/token")
    public ResponseEntity<Map<String, Object>> roomToken(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.roomToken(eventId, body), requestId, correlationId, false);
    }

    @PostMapping("/room/{eventId}/start")
    public ResponseEntity<Map<String, Object>> startRoom(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.startRoom(eventId), requestId, correlationId, false);
    }

    @PostMapping("/room/{eventId}/end")
    public ResponseEntity<Map<String, Object>> endRoom(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.endRoom(eventId), requestId, correlationId, false);
    }

    @PostMapping("/room/{eventId}/record")
    public ResponseEntity<Map<String, Object>> recordRoom(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.recordRoom(eventId), requestId, correlationId, false);
    }

    @GetMapping("/room/{eventId}/media-health")
    public ResponseEntity<Map<String, Object>> mediaHealth(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.mediaHealth(eventId), requestId, correlationId, false);
    }

    @GetMapping("/room/{eventId}/replay")
    public ResponseEntity<Map<String, Object>> getReplay(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.getReplay(eventId), requestId, correlationId, false);
    }

    @PostMapping("/room/{eventId}/process-replay")
    public ResponseEntity<Map<String, Object>> processReplay(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.processReplay(eventId), requestId, correlationId, false);
    }

    @PostMapping("/room/{eventId}/publish-replay")
    public ResponseEntity<Map<String, Object>> publishReplay(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.publishReplay(eventId), requestId, correlationId, false);
    }

    // ── Interactions ─────────────────────────────────────────────────

    @PostMapping("/interactions/{eventId}/questions")
    public ResponseEntity<Map<String, Object>> submitQuestion(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.submitQuestion(eventId, body), requestId, correlationId, true);
    }

    @GetMapping("/interactions/{eventId}/questions")
    public ResponseEntity<Map<String, Object>> listQuestions(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listQuestions(eventId), requestId, correlationId, false);
    }

    @PostMapping("/interactions/{eventId}/questions/{questionId}/upvote")
    public ResponseEntity<Map<String, Object>> upvoteQuestion(
            @PathVariable String eventId,
            @PathVariable String questionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.upvoteQuestion(eventId, questionId), requestId, correlationId, false);
    }

    @PostMapping("/interactions/{eventId}/chat")
    public ResponseEntity<Map<String, Object>> postChat(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.postChat(eventId, body), requestId, correlationId, true);
    }

    @GetMapping("/interactions/{eventId}/chat")
    public ResponseEntity<Map<String, Object>> listChat(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listChat(eventId), requestId, correlationId, false);
    }

    @PostMapping("/interactions/{eventId}/polls")
    public ResponseEntity<Map<String, Object>> createPoll(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.createPoll(eventId, body), requestId, correlationId, true);
    }

    @PostMapping("/interactions/{eventId}/polls/{pollId}/activate")
    public ResponseEntity<Map<String, Object>> activatePoll(
            @PathVariable String eventId,
            @PathVariable String pollId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.activatePoll(eventId, pollId), requestId, correlationId, false);
    }

    @PostMapping("/interactions/{eventId}/polls/{pollId}/respond")
    public ResponseEntity<Map<String, Object>> respondPoll(
            @PathVariable String eventId,
            @PathVariable String pollId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.respondPoll(eventId, pollId, body), requestId, correlationId, true);
    }

    @GetMapping("/interactions/{eventId}/polls")
    public ResponseEntity<Map<String, Object>> listPolls(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listPolls(eventId), requestId, correlationId, false);
    }

    @PostMapping("/interactions/{eventId}/feedback")
    public ResponseEntity<Map<String, Object>> submitFeedback(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.submitFeedback(eventId, body), requestId, correlationId, true);
    }

    @GetMapping("/interactions/{eventId}/resources")
    public ResponseEntity<Map<String, Object>> listResources(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listResources(eventId), requestId, correlationId, false);
    }

    @PostMapping("/interactions/{eventId}/resources")
    public ResponseEntity<Map<String, Object>> addResource(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.addResource(eventId, body), requestId, correlationId, true);
    }

    // ── Moderation ───────────────────────────────────────────────────

    @PostMapping("/moderation/{eventId}/chat/{messageId}")
    public ResponseEntity<Map<String, Object>> moderateChat(
            @PathVariable String eventId,
            @PathVariable String messageId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.moderateChat(eventId, messageId, body), requestId, correlationId, false);
    }

    @PostMapping("/moderation/{eventId}/questions/{questionId}")
    public ResponseEntity<Map<String, Object>> moderateQuestion(
            @PathVariable String eventId,
            @PathVariable String questionId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.moderateQuestion(eventId, questionId, body), requestId, correlationId, false);
    }

    @PutMapping("/moderation/{eventId}/chat-enabled")
    public ResponseEntity<Map<String, Object>> setChatEnabled(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.setChatEnabled(eventId, body), requestId, correlationId, false);
    }

    @PutMapping("/moderation/{eventId}/qna-enabled")
    public ResponseEntity<Map<String, Object>> setQnaEnabled(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.setQnaEnabled(eventId, body), requestId, correlationId, false);
    }

    // ── Attendance ───────────────────────────────────────────────────

    @PostMapping("/attendance/{eventId}/join")
    public ResponseEntity<Map<String, Object>> attendanceJoin(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.attendanceJoin(eventId, body), requestId, correlationId, true);
    }

    @PostMapping("/attendance/{eventId}/leave")
    public ResponseEntity<Map<String, Object>> attendanceLeave(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.attendanceLeave(eventId, body), requestId, correlationId, false);
    }

    @PutMapping("/attendance/{eventId}/participants/{participantId}/minutes")
    public ResponseEntity<Map<String, Object>> trackMinutes(
            @PathVariable String eventId,
            @PathVariable String participantId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.trackMinutes(eventId, participantId, body), requestId, correlationId, false);
    }

    @GetMapping("/attendance/{eventId}")
    public ResponseEntity<Map<String, Object>> listAttendance(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listAttendance(eventId), requestId, correlationId, false);
    }

    @GetMapping("/attendance/{eventId}/participants/{participantId}")
    public ResponseEntity<Map<String, Object>> getAttendance(
            @PathVariable String eventId,
            @PathVariable String participantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.getAttendance(eventId, participantId), requestId, correlationId, false);
    }

    // ── Certificates ─────────────────────────────────────────────────

    @PostMapping("/certificates/{eventId}/attendance/{participantId}")
    public ResponseEntity<Map<String, Object>> issueAttendanceCertificate(
            @PathVariable String eventId,
            @PathVariable String participantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.issueAttendanceCertificate(eventId, participantId), requestId, correlationId, true);
    }

    @PostMapping("/certificates/{eventId}/cpd/{participantId}")
    public ResponseEntity<Map<String, Object>> issueCpdCertificate(
            @PathVariable String eventId,
            @PathVariable String participantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.issueCpdCertificate(eventId, participantId), requestId, correlationId, true);
    }

    @GetMapping("/certificates/{eventId}")
    public ResponseEntity<Map<String, Object>> listCertificates(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listCertificates(eventId), requestId, correlationId, false);
    }

    @GetMapping("/certificates/verify/{verificationCode}")
    public ResponseEntity<Map<String, Object>> verifyCertificate(
            @PathVariable String verificationCode,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.verifyCertificate(verificationCode), requestId, correlationId, false);
    }

    // ── Analytics ────────────────────────────────────────────────────

    @PostMapping("/analytics/{eventId}/snapshot")
    public ResponseEntity<Map<String, Object>> captureSnapshot(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.captureSnapshot(eventId), requestId, correlationId, false);
    }

    @GetMapping("/analytics/{eventId}/dashboard")
    public ResponseEntity<Map<String, Object>> analyticsDashboard(
            @PathVariable String eventId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.analyticsDashboard(eventId), requestId, correlationId, false);
    }

    @GetMapping("/analytics/{eventId}/history")
    public ResponseEntity<Map<String, Object>> analyticsHistory(
            @PathVariable String eventId,
            @RequestParam String metricName,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.analyticsHistory(eventId, metricName), requestId, correlationId, false);
    }

    // ── Discovery ────────────────────────────────────────────────────

    @GetMapping("/discover/context/{contextType}")
    public ResponseEntity<Map<String, Object>> discoverByContext(
            @PathVariable String contextType,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.discoverByContext(contextType), requestId, correlationId, false);
    }

    @GetMapping("/discover/facility/{facilityId}")
    public ResponseEntity<Map<String, Object>> discoverByFacility(
            @PathVariable String facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.discoverByFacility(facilityId), requestId, correlationId, false);
    }

    @GetMapping("/discover/role")
    public ResponseEntity<Map<String, Object>> discoverByRole(
            @RequestParam String userId,
            @RequestParam String role,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.discoverByRole(userId, role), requestId, correlationId, false);
    }

    @FunctionalInterface
    private interface LiveCall {
        JsonNode call() throws Exception;
    }

    private ResponseEntity<Map<String, Object>> proxy(
            LiveCall call,
            String requestId,
            String correlationId,
            boolean created) {
        try {
            JsonNode body = call.call();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", body != null ? body : Map.of());
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return created
                    ? ResponseEntity.status(HttpStatus.CREATED).body(response)
                    : ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("LIVE proxy failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", List.of(),
                    "error", Map.of("code", "LIVE_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
