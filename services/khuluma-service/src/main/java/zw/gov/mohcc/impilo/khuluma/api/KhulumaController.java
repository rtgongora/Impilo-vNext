package zw.gov.mohcc.impilo.khuluma.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.khuluma.api.dto.KhulumaDtos.*;
import zw.gov.mohcc.impilo.khuluma.core.ActorContext;
import zw.gov.mohcc.impilo.khuluma.core.ActorContextResolver;
import zw.gov.mohcc.impilo.khuluma.core.CallResult;
import zw.gov.mohcc.impilo.khuluma.core.CallService;
import zw.gov.mohcc.impilo.khuluma.core.ConversationService;
import zw.gov.mohcc.impilo.khuluma.core.ConversationSummary;
import zw.gov.mohcc.impilo.khuluma.core.MeetingJoin;
import zw.gov.mohcc.impilo.khuluma.core.MeetingResult;
import zw.gov.mohcc.impilo.khuluma.core.MeetingService;
import zw.gov.mohcc.impilo.khuluma.core.MessageService;
import zw.gov.mohcc.impilo.khuluma.core.PresenceService;
import zw.gov.mohcc.impilo.khuluma.domain.CallEntity;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationEntity;
import zw.gov.mohcc.impilo.khuluma.domain.MessageEntity;
import zw.gov.mohcc.impilo.khuluma.domain.PresenceEntity;

import java.util.List;
import java.util.UUID;

/**
 * Internal API for the Khuluma Comms Hub, consumed by experience-bff. Every endpoint resolves
 * the acting trust context (tenant + actor) and delegates to a core service; the services
 * enforce membership, append audit/outbox events, and push realtime.
 */
@RestController
@RequestMapping("/internal/v1/khuluma")
public class KhulumaController {

    private final ActorContextResolver contextResolver;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final PresenceService presenceService;
    private final CallService callService;
    private final MeetingService meetingService;

    public KhulumaController(ActorContextResolver contextResolver,
                             ConversationService conversationService,
                             MessageService messageService,
                             PresenceService presenceService,
                             CallService callService,
                             MeetingService meetingService) {
        this.contextResolver = contextResolver;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.presenceService = presenceService;
        this.callService = callService;
        this.meetingService = meetingService;
    }

    // ── conversations ─────────────────────────────────────────────────────────

    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> create(@RequestBody CreateConversationRequest req) {
        ActorContext ctx = contextResolver.resolve();
        List<ConversationService.NewParticipant> participants = req.participants() == null ? List.of()
                : req.participants().stream()
                    .map(p -> new ConversationService.NewParticipant(p.actorId(), p.actorType(), p.displayName(), p.role()))
                    .toList();
        List<ConversationService.NewLink> links = req.links() == null ? List.of()
                : req.links().stream()
                    .map(l -> new ConversationService.NewLink(l.objectType(), l.objectId(), l.linkRole(), l.note()))
                    .toList();
        ConversationEntity conv = conversationService.create(ctx, req.type(), req.title(), req.topic(), participants, links);
        return ResponseEntity.status(HttpStatus.CREATED).body(detail(ctx, conv.getConversationId()));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationSummaryResponse>> inbox() {
        ActorContext ctx = contextResolver.resolve();
        List<ConversationSummaryResponse> rows = conversationService.inbox(ctx).stream()
                .map(ConversationSummaryResponse::of).toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ConversationResponse> getConversation(@PathVariable UUID id) {
        ActorContext ctx = contextResolver.resolve();
        return ResponseEntity.ok(detail(ctx, id));
    }

    @PostMapping("/conversations/{id}/participants")
    public ResponseEntity<ConversationResponse> addParticipant(@PathVariable UUID id,
                                                               @RequestBody AddParticipantRequest req) {
        ActorContext ctx = contextResolver.resolve();
        conversationService.addParticipant(ctx, id, req.actorId(), req.actorType(), req.displayName(), req.role());
        return ResponseEntity.ok(detail(ctx, id));
    }

    @DeleteMapping("/conversations/{id}/participants/{actorId}")
    public ResponseEntity<ConversationResponse> removeParticipant(@PathVariable UUID id, @PathVariable String actorId) {
        ActorContext ctx = contextResolver.resolve();
        conversationService.removeParticipant(ctx, id, actorId);
        return ResponseEntity.ok(detail(ctx, id));
    }

    @PostMapping("/conversations/{id}/links")
    public ResponseEntity<ConversationResponse> link(@PathVariable UUID id, @RequestBody LinkInput req) {
        ActorContext ctx = contextResolver.resolve();
        conversationService.linkObject(ctx, id, req.objectType(), req.objectId(), req.linkRole(), req.note());
        return ResponseEntity.ok(detail(ctx, id));
    }

    // ── messages ──────────────────────────────────────────────────────────────

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<MessageResponse>> messages(@PathVariable UUID id) {
        ActorContext ctx = contextResolver.resolve();
        List<MessageResponse> out = messageService.list(ctx, id).stream().map(MessageResponse::of).toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<MessageResponse> send(@PathVariable UUID id, @RequestBody SendMessageRequest req) {
        ActorContext ctx = contextResolver.resolve();
        MessageEntity msg = messageService.send(ctx, id, req.body(), req.contentType(), req.clientMessageId(), req.metadata());
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.of(msg));
    }

    @PostMapping("/conversations/{id}/messages/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, @RequestBody MarkReadRequest req) {
        ActorContext ctx = contextResolver.resolve();
        messageService.markRead(ctx, id, UUID.fromString(req.messageId()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount() {
        ActorContext ctx = contextResolver.resolve();
        List<ConversationSummary> inbox = conversationService.inbox(ctx);
        long total = inbox.stream().mapToLong(ConversationSummary::unreadCount).sum();
        int withUnread = (int) inbox.stream().filter(s -> s.unreadCount() > 0).count();
        return ResponseEntity.ok(new UnreadCountResponse(total, withUnread));
    }

    // ── presence ──────────────────────────────────────────────────────────────

    @PutMapping("/presence")
    public ResponseEntity<PresenceResponse> updatePresence(@RequestBody PresenceRequest req) {
        ActorContext ctx = contextResolver.resolve();
        PresenceEntity p = presenceService.update(ctx, req.status(), req.statusMessage(), req.device());
        return ResponseEntity.ok(PresenceResponse.of(p));
    }

    @GetMapping("/presence/{actorId}")
    public ResponseEntity<PresenceResponse> getPresence(@PathVariable String actorId) {
        ActorContext ctx = contextResolver.resolve();
        return ResponseEntity.ok(PresenceResponse.of(presenceService.get(ctx.tenantId(), actorId)));
    }

    // ── calls ─────────────────────────────────────────────────────────────────

    @PostMapping("/calls")
    public ResponseEntity<CallResponse> startCall(@RequestBody StartCallRequest req) {
        ActorContext ctx = contextResolver.resolve();
        UUID conversationId = req.conversationId() != null && !req.conversationId().isBlank()
                ? UUID.fromString(req.conversationId()) : null;
        List<CallService.Callee> callees = req.callees() == null ? List.of()
                : req.callees().stream()
                    .map(c -> new CallService.Callee(c.actorId(), c.actorType(), c.displayName())).toList();
        CallResult result = callService.start(ctx, conversationId, req.callType(), req.displayName(), callees);
        return ResponseEntity.status(HttpStatus.CREATED).body(callResponse(result));
    }

    @GetMapping("/calls/incoming")
    public ResponseEntity<List<CallResponse>> incomingCalls() {
        ActorContext ctx = contextResolver.resolve();
        List<CallResponse> rows = callService.incoming(ctx).stream()
                .map(c -> CallResponse.of(c, false, null, null, null, callService.participantsOf(c.getCallId())))
                .toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/calls/{id}")
    public ResponseEntity<CallResponse> getCall(@PathVariable UUID id) {
        ActorContext ctx = contextResolver.resolve();
        CallEntity call = callService.get(ctx, id);
        return ResponseEntity.ok(CallResponse.of(call, call.getRtcSessionId() != null, null, null, null,
                callService.participantsOf(id)));
    }

    @PostMapping("/calls/{id}/accept")
    public ResponseEntity<CallResponse> acceptCall(@PathVariable UUID id, @RequestBody(required = false) PresenceRequest ignored) {
        ActorContext ctx = contextResolver.resolve();
        CallResult result = callService.accept(ctx, id, null);
        return ResponseEntity.ok(callResponse(result));
    }

    @PostMapping("/calls/{id}/decline")
    public ResponseEntity<CallResponse> declineCall(@PathVariable UUID id) {
        ActorContext ctx = contextResolver.resolve();
        CallEntity call = callService.decline(ctx, id);
        return ResponseEntity.ok(CallResponse.of(call, false, null, null, null, callService.participantsOf(id)));
    }

    @PostMapping("/calls/{id}/end")
    public ResponseEntity<CallResponse> endCall(@PathVariable UUID id, @RequestBody(required = false) EndCallRequest req) {
        ActorContext ctx = contextResolver.resolve();
        CallEntity call = callService.end(ctx, id, req != null ? req.reason() : null);
        return ResponseEntity.ok(CallResponse.of(call, false, null, null, null, callService.participantsOf(id)));
    }

    @PostMapping("/calls/{id}/signals")
    public ResponseEntity<Void> signal(@PathVariable UUID id, @RequestBody SignalRequest req) {
        ActorContext ctx = contextResolver.resolve();
        callService.signal(ctx, id, req.signalType(), req.detail());
        return ResponseEntity.accepted().build();
    }

    // ── meetings / live events (orchestrate live-service) ─────────────────────

    @PostMapping("/meetings")
    public ResponseEntity<MeetingResponse> createMeeting(@RequestBody CreateMeetingRequest req) {
        ActorContext ctx = contextResolver.resolve();
        List<ConversationService.NewParticipant> participants = req.participants() == null ? List.of()
                : req.participants().stream()
                    .map(p -> new ConversationService.NewParticipant(p.actorId(), p.actorType(), p.displayName(), p.role()))
                    .toList();
        MeetingResult result = meetingService.create(ctx, req.title(), participants);
        return ResponseEntity.status(HttpStatus.CREATED).body(new MeetingResponse(
                result.conversationId(), result.eventId(), result.available(), null, null, null, result.error()));
    }

    /** Attach a Khuluma conversation to an existing Impilo Live event (compose, don't duplicate). */
    @PostMapping("/meetings/from-event")
    public ResponseEntity<MeetingResponse> meetingFromEvent(@RequestBody MeetingFromEventRequest req) {
        ActorContext ctx = contextResolver.resolve();
        List<ConversationService.NewParticipant> participants = req.participants() == null ? List.of()
                : req.participants().stream()
                    .map(p -> new ConversationService.NewParticipant(p.actorId(), p.actorType(), p.displayName(), p.role()))
                    .toList();
        MeetingResult result = meetingService.fromEvent(ctx, req.eventId(), req.title(), participants);
        return ResponseEntity.status(HttpStatus.CREATED).body(new MeetingResponse(
                result.conversationId(), result.eventId(), result.available(), null, null, null, result.error()));
    }

    /** The Khuluma conversation anchored to an Impilo Live event, for the Impilo Live experience. */
    @GetMapping("/events/{eventId}/conversation")
    public ResponseEntity<MeetingResponse> eventConversation(@PathVariable String eventId) {
        ActorContext ctx = contextResolver.resolve();
        return meetingService.conversationForEvent(ctx, eventId)
                .map(convId -> ResponseEntity.ok(new MeetingResponse(convId, eventId, false, null, null, null, null)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/conversations/{id}/meeting/join")
    public ResponseEntity<MeetingResponse> joinMeeting(@PathVariable UUID id) {
        ActorContext ctx = contextResolver.resolve();
        MeetingJoin join = meetingService.join(ctx, id);
        return ResponseEntity.ok(new MeetingResponse(join.conversationId(), join.eventId(),
                join.mediaAvailable(), join.roomUrl(), join.accessToken(), join.provider(), join.error()));
    }

    @PostMapping("/conversations/{id}/meeting/end")
    public ResponseEntity<Void> endMeeting(@PathVariable UUID id) {
        ActorContext ctx = contextResolver.resolve();
        meetingService.end(ctx, id);
        return ResponseEntity.accepted().build();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private ConversationResponse detail(ActorContext ctx, UUID conversationId) {
        ConversationEntity conv = conversationService.get(ctx, conversationId);
        return ConversationResponse.of(conv,
                conversationService.participantsOf(conversationId),
                conversationService.linksOf(conversationId));
    }

    private CallResponse callResponse(CallResult result) {
        CallEntity call = result.call();
        return CallResponse.of(call, result.mediaAvailable(), result.roomUrl(), result.accessToken(),
                result.mediaError(), callService.participantsOf(call.getCallId()));
    }
}
