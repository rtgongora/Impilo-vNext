package zw.gov.mohcc.impilo.live.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.live.api.dto.LiveDtos;
import zw.gov.mohcc.impilo.live.core.InteractionService;
import zw.gov.mohcc.impilo.live.persistence.entity.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/live/interactions")
public class LiveInteractionController {

    private final InteractionService interactionService;

    public LiveInteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping("/{eventId}/questions")
    public ResponseEntity<LiveEventQuestionEntity> submitQuestion(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @RequestBody LiveDtos.QuestionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                interactionService.submitQuestion(tenantId, eventId, req.participantId(),
                        req.participantType(), req.questionText(), req.anonymousAllowed()));
    }

    @GetMapping("/{eventId}/questions")
    public List<LiveEventQuestionEntity> listQuestions(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId) {
        return interactionService.listQuestions(tenantId, eventId);
    }

    @PostMapping("/{eventId}/questions/{questionId}/upvote")
    public LiveEventQuestionEntity upvote(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @PathVariable UUID questionId) {
        return interactionService.upvoteQuestion(tenantId, eventId, questionId);
    }

    @PostMapping("/{eventId}/chat")
    public ResponseEntity<LiveEventChatMessageEntity> postChat(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @RequestBody LiveDtos.ChatRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                interactionService.postChat(tenantId, eventId, req.participantId(),
                        req.participantType(), req.message()));
    }

    @GetMapping("/{eventId}/chat")
    public List<LiveEventChatMessageEntity> listChat(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId) {
        return interactionService.listChat(tenantId, eventId);
    }

    @PostMapping("/{eventId}/polls")
    public ResponseEntity<LiveEventPollEntity> createPoll(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @RequestBody LiveDtos.PollRequest req) {
        String optionsJson = req.options() != null ? "[\"" + String.join("\",\"", req.options()) + "\"]" : "[]";
        return ResponseEntity.status(HttpStatus.CREATED).body(
                interactionService.createPoll(tenantId, eventId, req.question(), optionsJson, req.createdBy()));
    }

    @PostMapping("/{eventId}/polls/{pollId}/activate")
    public LiveEventPollEntity activatePoll(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @PathVariable UUID pollId) {
        return interactionService.activatePoll(tenantId, eventId, pollId);
    }

    @PostMapping("/{eventId}/polls/{pollId}/respond")
    public ResponseEntity<LiveEventPollResponseEntity> respondPoll(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @PathVariable UUID pollId,
            @RequestBody LiveDtos.PollResponseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                interactionService.respondToPoll(tenantId, eventId, pollId,
                        req.participantId(), req.selectedOption()));
    }

    @GetMapping("/{eventId}/polls")
    public List<LiveEventPollEntity> listPolls(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId) {
        return interactionService.listPolls(tenantId, eventId);
    }

    @GetMapping("/{eventId}/resources")
    public List<LiveEventResourceEntity> listResources(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId) {
        return interactionService.listResources(tenantId, eventId);
    }

    @PostMapping("/{eventId}/resources")
    public ResponseEntity<LiveEventResourceEntity> addResource(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @RequestBody java.util.Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                interactionService.addResource(
                        tenantId,
                        eventId,
                        String.valueOf(body.getOrDefault("title", "Resource")),
                        String.valueOf(body.getOrDefault("resourceType", "DOCUMENT")),
                        body.get("fileId") != null ? String.valueOf(body.get("fileId")) : null,
                        body.get("visibility") != null ? String.valueOf(body.get("visibility")) : "ATTENDEES"));
    }

    @PostMapping("/{eventId}/feedback")
    public ResponseEntity<LiveEventFeedbackEntity> submitFeedback(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @RequestBody java.util.Map<String, Object> body) {
        String participantId = String.valueOf(body.get("participantId"));
        Integer rating = body.get("rating") instanceof Number n ? n.intValue() : null;
        String comments = body.get("comments") != null ? String.valueOf(body.get("comments")) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(
                interactionService.submitFeedback(tenantId, eventId, participantId, rating, comments));
    }
}
