package zw.gov.mohcc.impilo.khuluma.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.khuluma.client.LiveServiceClient;
import zw.gov.mohcc.impilo.khuluma.core.ActorContext;
import zw.gov.mohcc.impilo.khuluma.core.ConversationService;
import zw.gov.mohcc.impilo.khuluma.core.MeetingJoin;
import zw.gov.mohcc.impilo.khuluma.core.MeetingResult;
import zw.gov.mohcc.impilo.khuluma.core.MeetingService;
import zw.gov.mohcc.impilo.khuluma.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Meeting orchestration: a MEETING conversation is created + linked to a live-service event, the
 * creator/participants get a real media token via live-service (mocked here), membership is
 * enforced, and every step is audited. (LiveServiceClient is mocked; LiveServiceClientTest proves
 * the real request/response shapes.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class MeetingServiceTest {

    @Autowired private MeetingService meetingService;
    @Autowired private ConversationService conversationService;
    @Autowired private OutboxEventRepository outbox;

    @MockBean private LiveServiceClient liveService;

    private static ActorContext ctx(UUID tenant, String actor) {
        return new ActorContext(tenant, "national-spine", actor, "PROVIDER", UUID.randomUUID().toString());
    }

    @Test
    void create_makesMeetingConversation_linksEvent_andAudits() {
        UUID tenant = UUID.randomUUID();
        ActorContext a = ctx(tenant, "provider-a");
        when(liveService.createEvent(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new LiveServiceClient.CreateEventResult(true, "ev-9", "DRAFT", null));

        MeetingResult result = meetingService.create(a, "Ward huddle",
                List.of(new ConversationService.NewParticipant("provider-b", "PROVIDER", "Dr B", "MEMBER")));

        assertThat(result.available()).isTrue();
        assertThat(result.eventId()).isEqualTo("ev-9");
        UUID convId = UUID.fromString(result.conversationId());
        assertThat(conversationService.get(a, convId).getConversationType()).isEqualTo("MEETING");
        assertThat(conversationService.linksOf(convId))
                .anyMatch(l -> l.getObjectType().equals("LIVE_EVENT") && l.getObjectId().equals("ev-9"));
        assertThat(outbox.findAll()).anyMatch(e -> e.getEventType().equals("impilo.khuluma.meeting.created.v1"));
    }

    @Test
    void fromEvent_attachesConversationToImpiloLiveEvent_idempotently() {
        UUID tenant = UUID.randomUUID();
        ActorContext a = ctx(tenant, "provider-a");

        MeetingResult first = meetingService.fromEvent(a, "live-event-42", "Webinar chat",
                List.of(new ConversationService.NewParticipant("provider-b", "PROVIDER", "Dr B", "MEMBER")));
        assertThat(first.available()).isTrue();
        assertThat(first.eventId()).isEqualTo("live-event-42");
        UUID convId = UUID.fromString(first.conversationId());
        assertThat(conversationService.linksOf(convId))
                .anyMatch(l -> l.getObjectType().equals("LIVE_EVENT") && l.getObjectId().equals("live-event-42"));

        // Idempotent: a second attach reuses the same conversation.
        MeetingResult second = meetingService.fromEvent(a, "live-event-42", "Webinar chat", List.of());
        assertThat(second.conversationId()).isEqualTo(first.conversationId());

        // Impilo Live can look up the anchored conversation.
        assertThat(meetingService.conversationForEvent(a, "live-event-42")).contains(first.conversationId());
        assertThat(meetingService.conversationForEvent(a, "no-such-event")).isEmpty();
        assertThat(outbox.findAll()).anyMatch(e -> e.getEventType().equals("impilo.khuluma.meeting.from-event.v1"));
    }

    @Test
    void join_mintsMediaToken_forParticipant() {
        UUID tenant = UUID.randomUUID();
        ActorContext a = ctx(tenant, "provider-a");
        ActorContext b = ctx(tenant, "provider-b");
        ActorContext stranger = ctx(tenant, "provider-x");

        when(liveService.createEvent(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new LiveServiceClient.CreateEventResult(true, "ev-7", "DRAFT", null));
        when(liveService.issueToken(eq("ev-7"), anyString(), anyString()))
                .thenReturn(new LiveServiceClient.RoomTokenResult(true, "room-7", "ws://livekit:7880",
                        "real-jwt", "RTC_GATEWAY", "live", null));

        MeetingResult meeting = meetingService.create(a, "Case review",
                List.of(new ConversationService.NewParticipant("provider-b", "PROVIDER", "Dr B", "MEMBER")));
        UUID convId = UUID.fromString(meeting.conversationId());

        MeetingJoin join = meetingService.join(b, convId);
        assertThat(join.mediaAvailable()).isTrue();
        assertThat(join.accessToken()).isEqualTo("real-jwt");
        assertThat(join.roomUrl()).isEqualTo("ws://livekit:7880");
        verify(liveService).joinRoom(eq("ev-7"), eq("provider-b"), anyString(), any());

        // A non-participant cannot join.
        assertThatThrownBy(() -> meetingService.join(stranger, convId)).isInstanceOf(SecurityException.class);
    }
}
