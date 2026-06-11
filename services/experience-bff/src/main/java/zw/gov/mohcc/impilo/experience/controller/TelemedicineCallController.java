package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineCallInviteService;

import java.util.Map;

/**
 * Incoming telemedicine call invites — pushes to citizen presence WebSocket.
 */
@RestController
@RequestMapping("/internal/v1/telemedicine")
public class TelemedicineCallController {

    private final TelemedicineCallInviteService inviteService;

    public TelemedicineCallController(TelemedicineCallInviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping("/citizen/profile")
    public ResponseEntity<Map<String, Object>> citizenProfile(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestParam(required = false) String email) {
        Map<String, Object> profile = inviteService.resolveCitizenProfile(email);
        if (profile.isEmpty() && actorId != null) {
            profile = Map.of("actorId", actorId);
        }
        return ResponseEntity.ok(Map.of(
                "data", profile,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/calls/invite")
    public ResponseEntity<Map<String, Object>> inviteCall(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) throws Exception {

        String sessionId = stringVal(body, "sessionId");
        String patientId = stringVal(body, "patientId");
        String targetUserId = stringVal(body, "targetUserId");
        if (sessionId.isBlank() || (patientId.isBlank() && targetUserId.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "INVALID_REQUEST", "message", "sessionId and patientId or targetUserId are required")
            ));
        }

        String callerName = stringVal(body, "callerName");
        String callType = stringVal(body, "callType");
        if (callType.isBlank()) {
            callType = "audio";
        }

        String caller = actorId != null ? actorId : stringVal(body, "callerId");
        Map<String, Object> invite = !targetUserId.isBlank()
                ? inviteService.sendInviteToUser(sessionId, targetUserId, caller, callerName, callType)
                : inviteService.sendInvite(sessionId, patientId, caller, callerName, callType);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", invite,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @GetMapping("/calls/pending")
    public ResponseEntity<Map<String, Object>> pendingCall(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String patientId) {
        Map<String, Object> pending = inviteService.pollPending(patientId);
        return ResponseEntity.ok(Map.of(
                "data", pending != null ? pending : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @GetMapping("/calls/pending-user")
    public ResponseEntity<Map<String, Object>> pendingCallForUser(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String userId) {
        Map<String, Object> pending = inviteService.pollPendingForUser(userId);
        return ResponseEntity.ok(Map.of(
                "data", pending != null ? pending : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/calls/{sessionId}/accept")
    public ResponseEntity<Map<String, Object>> acceptCall(
            @PathVariable String sessionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        if (body != null) {
            inviteService.clearPending(sessionId, stringVal(body, "patientId"));
            inviteService.clearPendingForUser(sessionId, stringVal(body, "targetUserId"));
        }
        return ResponseEntity.ok(Map.of(
                "data", Map.of("sessionId", sessionId, "status", "ACCEPTED"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/calls/{sessionId}/decline")
    public ResponseEntity<Map<String, Object>> declineCall(
            @PathVariable String sessionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        inviteService.clearPending(sessionId, stringVal(body, "patientId"));
        inviteService.clearPendingForUser(sessionId, stringVal(body, "targetUserId"));
        return ResponseEntity.ok(Map.of(
                "data", Map.of("sessionId", sessionId, "status", "DECLINED"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    private static String stringVal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value != null ? value.toString().trim() : "";
    }
}
