package zw.gov.mohcc.impilo.live.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.live.api.dto.LiveDtos;
import zw.gov.mohcc.impilo.live.core.StageService;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventStageRequestEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LIVE_EVENT stage management: audience requests the stage; the crew
 * (host/producer, resolved server-side) approves, denies, or demotes.
 * Decisions are attributed to X-Actor-ID — never to a body-asserted actor.
 */
@RestController
@RequestMapping("/internal/v1/live/stage")
public class LiveStageController {

    private final StageService stageService;

    public LiveStageController(StageService stageService) {
        this.stageService = stageService;
    }

    @PostMapping("/{eventId}/requests")
    public ResponseEntity<LiveEventStageRequestEntity> requestStage(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @RequestBody LiveDtos.StageRequestPayload req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                stageService.requestStage(tenantId, eventId, req.participantId(), req.participantType()));
    }

    @GetMapping("/{eventId}/requests")
    public List<LiveEventStageRequestEntity> listRequests(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @RequestParam(required = false) String status) {
        return stageService.list(tenantId, eventId, status);
    }

    @PostMapping("/{eventId}/requests/{requestId}/approve")
    public LiveEventStageRequestEntity approve(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID eventId,
            @PathVariable UUID requestId) {
        return stageService.approve(tenantId, eventId, requestId, actorId);
    }

    @PostMapping("/{eventId}/requests/{requestId}/deny")
    public LiveEventStageRequestEntity deny(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID eventId,
            @PathVariable UUID requestId) {
        return stageService.deny(tenantId, eventId, requestId, actorId);
    }

    /** Demote by participant: covers approved stage guests AND assigned speakers. */
    @PostMapping("/{eventId}/participants/{participantId}/demote")
    public Map<String, Object> demote(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID eventId,
            @PathVariable String participantId) {
        return stageService.demoteParticipant(tenantId, eventId, participantId, actorId);
    }

    /** Server-resolved role tier for the participant — the web's role truth. */
    @GetMapping("/{eventId}/participants/{participantId}/role")
    public Map<String, Object> myRole(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @PathVariable String participantId) {
        return stageService.myStageRole(tenantId, eventId, participantId);
    }
}
