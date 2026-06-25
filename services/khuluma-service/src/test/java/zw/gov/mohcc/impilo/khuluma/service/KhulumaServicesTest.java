package zw.gov.mohcc.impilo.khuluma.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.khuluma.core.ActorContext;
import zw.gov.mohcc.impilo.khuluma.core.CallResult;
import zw.gov.mohcc.impilo.khuluma.core.CallService;
import zw.gov.mohcc.impilo.khuluma.core.ConversationService;
import zw.gov.mohcc.impilo.khuluma.core.ConversationSummary;
import zw.gov.mohcc.impilo.khuluma.core.MessageService;
import zw.gov.mohcc.impilo.khuluma.core.PresenceService;
import zw.gov.mohcc.impilo.khuluma.domain.CallEntity;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationEntity;
import zw.gov.mohcc.impilo.khuluma.domain.MessageEntity;
import zw.gov.mohcc.impilo.khuluma.domain.PresenceEntity;
import zw.gov.mohcc.impilo.khuluma.repository.CallParticipantRepository;
import zw.gov.mohcc.impilo.khuluma.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Core-service behaviour for W1.2: conversation/message/presence/call domains, membership
 * enforcement, unread + receipts, and the audit/outbox trail. Calls rtc-gateway best-effort
 * (it is unreachable in test, so media is unavailable but the call lifecycle still proceeds).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class KhulumaServicesTest {

    @Autowired private ConversationService conversationService;
    @Autowired private MessageService messageService;
    @Autowired private PresenceService presenceService;
    @Autowired private CallService callService;
    @Autowired private CallParticipantRepository callParticipants;
    @Autowired private OutboxEventRepository outbox;

    private static ActorContext ctx(UUID tenant, String actor) {
        return new ActorContext(tenant, "national-spine", actor, "PROVIDER", UUID.randomUUID().toString());
    }

    @Test
    void createConversation_seedsCreatorParticipantLink_andEmitsEvent() {
        UUID tenant = UUID.randomUUID();
        ActorContext a = ctx(tenant, "provider-A");

        ConversationEntity conv = conversationService.create(a, "DIRECT", "Care coordination", null,
                List.of(new ConversationService.NewParticipant("provider-B", "PROVIDER", "Dr B", "MEMBER")),
                List.of(new ConversationService.NewLink("PATIENT", "cpid-123", "SUBJECT", null)));

        assertThat(conv.getConversationId()).isNotNull();
        assertThat(conversationService.participantsOf(conv.getConversationId()))
                .extracting(p -> p.getActorId()).contains("provider-A", "provider-B");
        assertThat(conversationService.linksOf(conv.getConversationId()))
                .anyMatch(l -> l.getObjectType().equals("PATIENT") && l.getObjectId().equals("cpid-123"));
        assertThat(outbox.findAll())
                .anyMatch(e -> e.getEventType().equals("impilo.khuluma.conversation.created.v1"));
    }

    @Test
    void sendMessage_enforcesMembership_updatesInbox_andUnread() {
        UUID tenant = UUID.randomUUID();
        ActorContext a = ctx(tenant, "provider-A");
        ActorContext b = ctx(tenant, "provider-B");
        ActorContext stranger = ctx(tenant, "provider-X");

        ConversationEntity conv = conversationService.create(a, "DIRECT", null, null,
                List.of(new ConversationService.NewParticipant("provider-B", "PROVIDER", "Dr B", "MEMBER")), null);
        UUID convId = conv.getConversationId();

        // Non-participant cannot post.
        assertThatThrownBy(() -> messageService.send(stranger, convId, "hi", "TEXT", null, null))
                .isInstanceOf(SecurityException.class);

        MessageEntity msg = messageService.send(a, convId, "Hello B", "TEXT", "client-1", null);
        assertThat(msg.getMessageId()).isNotNull();

        // Idempotent re-send returns the same message.
        MessageEntity resend = messageService.send(a, convId, "Hello B", "TEXT", "client-1", null);
        assertThat(resend.getMessageId()).isEqualTo(msg.getMessageId());

        // B sees 1 unread; A (the sender) sees 0.
        ConversationSummary bRow = conversationService.inbox(b).stream()
                .filter(s -> s.conversation().getConversationId().equals(convId)).findFirst().orElseThrow();
        assertThat(bRow.unreadCount()).isEqualTo(1);
        ConversationSummary aRow = conversationService.inbox(a).stream()
                .filter(s -> s.conversation().getConversationId().equals(convId)).findFirst().orElseThrow();
        assertThat(aRow.unreadCount()).isZero();

        // After B marks read, unread clears and a READ receipt exists.
        messageService.markRead(b, convId, msg.getMessageId());
        ConversationSummary bAfter = conversationService.inbox(b).stream()
                .filter(s -> s.conversation().getConversationId().equals(convId)).findFirst().orElseThrow();
        assertThat(bAfter.unreadCount()).isZero();
        assertThat(messageService.receiptsFor(msg.getMessageId()))
                .anyMatch(r -> r.getActorId().equals("provider-B") && r.getReceiptState().equals("READ"));

        assertThat(outbox.findAll()).anyMatch(e -> e.getEventType().equals("impilo.khuluma.message.sent.v1"));
        assertThat(outbox.findAll()).anyMatch(e -> e.getEventType().equals("impilo.khuluma.message.read.v1"));
    }

    @Test
    void presence_clampsUnknownStatus_andEmitsEvent() {
        UUID tenant = UUID.randomUUID();
        ActorContext a = ctx(tenant, "provider-A");

        PresenceEntity online = presenceService.update(a, "ONLINE", "On shift", "web");
        assertThat(online.getStatus()).isEqualTo("ONLINE");
        assertThat(online.getLastActiveAt()).isNotNull();

        PresenceEntity clamped = presenceService.update(a, "BOGUS", null, null);
        assertThat(clamped.getStatus()).isEqualTo("OFFLINE");

        assertThat(presenceService.get(tenant, "provider-A").getStatus()).isEqualTo("OFFLINE");
        assertThat(outbox.findAll()).anyMatch(e -> e.getEventType().equals("impilo.khuluma.presence.changed.v1"));
    }

    @Test
    void callLifecycle_ring_accept_end() {
        UUID tenant = UUID.randomUUID();
        ActorContext a = ctx(tenant, "provider-A");
        ActorContext b = ctx(tenant, "provider-B");

        ConversationEntity conv = conversationService.create(a, "DIRECT", null, null,
                List.of(new ConversationService.NewParticipant("provider-B", "PROVIDER", "Dr B", "MEMBER")), null);

        CallResult started = callService.start(a, conv.getConversationId(), "AUDIO", "Dr A",
                List.of(new CallService.Callee("provider-B", "PROVIDER", "Dr B")));
        CallEntity call = started.call();
        assertThat(call.getStatus()).isEqualTo("RINGING");
        assertThat(callParticipants.findByCallIdAndActorId(call.getCallId(), "provider-A").orElseThrow().getState())
                .isEqualTo("JOINED");
        assertThat(callParticipants.findByCallIdAndActorId(call.getCallId(), "provider-B").orElseThrow().getState())
                .isEqualTo("RINGING");
        assertThat(callService.eventsOf(call.getCallId())).anyMatch(e -> e.getEventType().equals("RING"));

        CallResult accepted = callService.accept(b, call.getCallId(), "Dr B");
        assertThat(accepted.call().getStatus()).isEqualTo("ACTIVE");
        assertThat(accepted.call().getStartedAt()).isNotNull();

        CallEntity ended = callService.end(a, call.getCallId(), "HANGUP");
        assertThat(ended.getStatus()).isEqualTo("ENDED");
        assertThat(ended.getEndedAt()).isNotNull();

        assertThat(outbox.findAll()).anyMatch(e -> e.getEventType().equals("impilo.khuluma.call.started.v1"));
        assertThat(outbox.findAll()).anyMatch(e -> e.getEventType().equals("impilo.khuluma.call.ended.v1"));
    }

    @Test
    void callDecline_marksDeclined() {
        UUID tenant = UUID.randomUUID();
        ActorContext a = ctx(tenant, "provider-A");
        ActorContext b = ctx(tenant, "provider-B");

        ConversationEntity conv = conversationService.create(a, "DIRECT", null, null,
                List.of(new ConversationService.NewParticipant("provider-B", "PROVIDER", "Dr B", "MEMBER")), null);
        CallResult started = callService.start(a, conv.getConversationId(), "VIDEO", "Dr A",
                List.of(new CallService.Callee("provider-B", "PROVIDER", "Dr B")));

        CallEntity declined = callService.decline(b, started.call().getCallId());
        assertThat(declined.getStatus()).isEqualTo("DECLINED");
        assertThat(callParticipants.findByCallIdAndActorId(declined.getCallId(), "provider-B").orElseThrow().getState())
                .isEqualTo("DECLINED");
    }
}
