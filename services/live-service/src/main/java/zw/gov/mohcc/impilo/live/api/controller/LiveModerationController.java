package zw.gov.mohcc.impilo.live.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.live.api.dto.LiveDtos;
import zw.gov.mohcc.impilo.live.core.ModerationService;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventChatMessageEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventQuestionEntity;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/live/moderation")
public class LiveModerationController {

    private final ModerationService moderationService;

    public LiveModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping("/{eventId}/chat/{messageId}")
    public LiveEventChatMessageEntity moderateChat(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @PathVariable UUID messageId,
            @RequestBody LiveDtos.ModerationRequest req) {
        return moderationService.moderateChat(tenantId, eventId, messageId,
                req.action(), req.moderatedBy());
    }

    @PostMapping("/{eventId}/questions/{questionId}")
    public LiveEventQuestionEntity moderateQuestion(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @PathVariable UUID questionId,
            @RequestBody LiveDtos.ModerationRequest req) {
        return moderationService.moderateQuestion(tenantId, eventId, questionId,
                req.action(), req.moderatedBy());
    }

    @PutMapping("/{eventId}/chat-enabled")
    public LiveEventEntity setChatEnabled(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID eventId,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return moderationService.disableChat(tenantId, eventId, enabled,
                actorId != null ? actorId : "moderator");
    }

    @PutMapping("/{eventId}/qna-enabled")
    public LiveEventEntity setQnaEnabled(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID eventId,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return moderationService.disableQna(tenantId, eventId, enabled,
                actorId != null ? actorId : "moderator");
    }
}
