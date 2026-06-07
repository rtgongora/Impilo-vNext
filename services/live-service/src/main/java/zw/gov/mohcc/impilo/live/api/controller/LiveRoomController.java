package zw.gov.mohcc.impilo.live.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.live.api.dto.LiveDtos;
import zw.gov.mohcc.impilo.live.core.LiveRoomService;
import zw.gov.mohcc.impilo.live.core.ReplayService;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventSessionEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/live/room")
public class LiveRoomController {

    private final LiveRoomService roomService;
    private final ReplayService replayService;

    public LiveRoomController(LiveRoomService roomService, ReplayService replayService) {
        this.roomService = roomService;
        this.replayService = replayService;
    }

    @PostMapping("/{eventId}/join")
    public ResponseEntity<Map<String, Object>> join(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @RequestBody LiveDtos.JoinRoomRequest req) {
        LiveEventSessionEntity session = roomService.join(tenantId, eventId,
                req.participantId(), req.participantType(),
                req.role() != null ? req.role() : "ATTENDEE");
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionMap(session));
    }

    @PostMapping("/{eventId}/token")
    public LiveDtos.RoomTokenResponse token(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @RequestBody LiveDtos.RoomTokenRequest req) {
        var result = roomService.token(tenantId, eventId, req.participantId(),
                req.role() != null ? req.role() : "ATTENDEE");
        return new LiveDtos.RoomTokenResponse(
                result.roomId(), result.roomUrl(), result.accessToken(),
                result.provider(), result.channel());
    }

    @PostMapping("/{eventId}/start")
    public Map<String, Object> start(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID eventId) {
        return sessionMap(roomService.start(tenantId, eventId,
                actorId != null ? actorId : "system"));
    }

    @PostMapping("/{eventId}/end")
    public Map<String, Object> end(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID eventId) {
        return sessionMap(roomService.end(tenantId, eventId,
                actorId != null ? actorId : "system"));
    }

    @PostMapping("/{eventId}/record")
    public Map<String, Object> record(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId) {
        return sessionMap(roomService.startRecording(tenantId, eventId));
    }

    @GetMapping("/{eventId}/media-health")
    public Map<String, Object> mediaHealth(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId) {
        return roomService.mediaHealth(tenantId, eventId);
    }

    @GetMapping("/{eventId}/replay")
    public Map<String, Object> getReplay(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID eventId) {
        return replayService.getReplay(tenantId, eventId, actorId != null ? actorId : "anonymous");
    }

    @PostMapping("/{eventId}/process-replay")
    public Map<String, Object> processReplay(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID eventId) {
        return replayService.processReplay(tenantId, eventId, actorId != null ? actorId : "system");
    }

    @PostMapping("/{eventId}/publish-replay")
    public Map<String, Object> publishReplay(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID eventId) {
        return replayService.publishReplay(tenantId, eventId, actorId != null ? actorId : "system");
    }

    private Map<String, Object> sessionMap(LiveEventSessionEntity session) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", session.getId());
        m.put("eventId", session.getEventId());
        m.put("providerType", session.getProviderType());
        m.put("providerRoomId", session.getProviderRoomId());
        m.put("healthStatus", session.getHealthStatus());
        m.put("startedAt", session.getStartedAt());
        m.put("endedAt", session.getEndedAt());
        m.put("recordingRef", session.getRecordingRef());
        m.put("playbackUrlRef", session.getPlaybackUrlRef());
        return m;
    }
}
