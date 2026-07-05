package zw.gov.mohcc.impilo.khuluma.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.khuluma.client.LiveServiceClient;
import zw.gov.mohcc.impilo.khuluma.client.RtcGatewayClient;
import zw.gov.mohcc.impilo.khuluma.core.ActorContext;
import zw.gov.mohcc.impilo.khuluma.core.ConversationService;
import zw.gov.mohcc.impilo.khuluma.core.MeetingJoin;
import zw.gov.mohcc.impilo.khuluma.core.MeetingResult;
import zw.gov.mohcc.impilo.khuluma.core.MeetingService;
import zw.gov.mohcc.impilo.khuluma.domain.MeetingActionItemEntity;
import zw.gov.mohcc.impilo.khuluma.domain.MeetingAdmissionEntity;
import zw.gov.mohcc.impilo.khuluma.repository.MeetingAdmissionRepository;
import zw.gov.mohcc.impilo.khuluma.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Meeting orchestration over Impilo Live + rtc-gateway: a MEETING conversation is created +
 * linked to a live-service event, tokens come from rtc-gateway against the MEETING template
 * (KNOCK lobby: hosts/cohosts bypass, members WAIT until admitted), lobby decisions drive
 * rtc-gateway and are mirrored into khuluma_meeting_admissions, and every step is audited.
 * (Clients are mocked; the client tests prove the real request/response shapes.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class MeetingServiceTest {

    @Autowired private MeetingService meetingService;
    @Autowired private ConversationService conversationService;
    @Autowired private MeetingAdmissionRepository admissions;
    @Autowired private OutboxEventRepository outbox;

    @MockBean private LiveServiceClient liveService;
    @MockBean private RtcGatewayClient rtcGateway;

    private static ActorContext ctx(UUID tenant, String actor) {
        return new ActorContext(tenant, "national-spine", actor, "PROVIDER", UUID.randomUUID().toString());
    }

    private void stubEventCreation(String eventId) {
        when(liveService.createEvent(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyInt(), nullable(String.class)))
                .thenReturn(new LiveServiceClient.CreateEventResult(true, eventId, "DRAFT", null));
    }

    private void stubRoom(String eventId, String rtcSessionId) {
        when(liveService.joinRoom(eq(eventId), anyString(), anyString(), anyString()))
                .thenReturn(new LiveServiceClient.JoinRoomResult(true, "live-sess-1", rtcSessionId, null));
    }

    private static RtcGatewayClient.RtcSessionResult ready(String token) {
        return new RtcGatewayClient.RtcSessionResult(true, "rtc-1", "livekit", "impilo-meet-x",
                "ws://livekit:7880", token, "PROVISIONED", null);
    }

    private static RtcGatewayClient.RtcSessionResult waiting() {
        return new RtcGatewayClient.RtcSessionResult(true, "rtc-1", null, null, null, null, "WAITING", null);
    }

    private MeetingResult meetingWith(ActorContext host, String member) {
        stubEventCreation("ev-1");
        return meetingService.create(host, "Case review",
                List.of(new ConversationService.NewParticipant(member, "PROVIDER", "Dr B", "MEMBER")));
    }

    @Test
    void create_makesMeetingConversation_linksEvent_andAudits() {
        ActorContext a = ctx(UUID.randomUUID(), "provider-a");
        stubEventCreation("ev-9");

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
    void create_passesScheduledAtThroughToLiveEvent() {
        ActorContext a = ctx(UUID.randomUUID(), "provider-a");
        stubEventCreation("ev-sched");

        meetingService.create(a, "Planning", List.of(), "2026-07-10T09:00:00Z");

        verify(liveService).createEvent(eq("Planning"), eq("PROFESSIONAL_MEETING"), eq("MEETING"), anyString(),
                eq("PROVIDER"), eq("provider-a"), anyInt(), eq("2026-07-10T09:00:00Z"));
    }

    @Test
    void fromEvent_attachesConversationToImpiloLiveEvent_idempotently() {
        ActorContext a = ctx(UUID.randomUUID(), "provider-a");

        MeetingResult first = meetingService.fromEvent(a, "live-event-42", "Webinar chat",
                List.of(new ConversationService.NewParticipant("provider-b", "PROVIDER", "Dr B", "MEMBER")));
        assertThat(first.available()).isTrue();
        MeetingResult second = meetingService.fromEvent(a, "live-event-42", "Webinar chat", List.of());
        assertThat(second.conversationId()).isEqualTo(first.conversationId());
        assertThat(meetingService.conversationForEvent(a, "live-event-42")).contains(first.conversationId());
    }

    @Test
    void join_asHost_bypassesLobby_andMintsHostToken() {
        UUID tenant = UUID.randomUUID();
        ActorContext host = ctx(tenant, "provider-a");
        MeetingResult meeting = meetingWith(host, "provider-b");
        UUID convId = UUID.fromString(meeting.conversationId());
        stubRoom("ev-1", "rtc-1");
        when(rtcGateway.issueToken(eq("rtc-1"), eq("provider-a"), anyString(), eq("HOST")))
                .thenReturn(ready("host-jwt"));

        MeetingJoin join = meetingService.join(host, convId);

        assertThat(join.status()).isEqualTo(MeetingJoin.STATUS_READY);
        assertThat(join.role()).isEqualTo("HOST");
        assertThat(join.accessToken()).isEqualTo("host-jwt");
        assertThat(join.rtcSessionId()).isEqualTo("rtc-1");
        MeetingAdmissionEntity admission = admissions.findByConversationIdAndActorId(convId, "provider-a").orElseThrow();
        assertThat(admission.getStatus()).isEqualTo(MeetingAdmissionEntity.STATUS_ADMITTED);
        assertThat(admission.getDecidedBy()).isEqualTo("ROOM_ADMIN_AUTO");
    }

    @Test
    void join_asMember_knocks_waits_thenAdmitYieldsToken() {
        UUID tenant = UUID.randomUUID();
        ActorContext host = ctx(tenant, "provider-a");
        ActorContext member = ctx(tenant, "provider-b");
        MeetingResult meeting = meetingWith(host, "provider-b");
        UUID convId = UUID.fromString(meeting.conversationId());
        stubRoom("ev-1", "rtc-1");
        when(rtcGateway.issueToken(eq("rtc-1"), eq("provider-b"), anyString(), eq("PARTICIPANT")))
                .thenReturn(waiting());

        MeetingJoin knock = meetingService.join(member, convId);
        assertThat(knock.status()).isEqualTo(MeetingJoin.STATUS_WAITING);
        assertThat(knock.accessToken()).isNull();
        assertThat(admissions.findByConversationIdAndActorId(convId, "provider-b").orElseThrow().getStatus())
                .isEqualTo(MeetingAdmissionEntity.STATUS_WAITING);
        assertThat(outbox.findAll())
                .anyMatch(e -> e.getEventType().equals("impilo.khuluma.meeting.admission-requested.v1"));

        // Host admits: rtc-gateway is driven first, the mirror updates, audit lands.
        when(rtcGateway.admit("rtc-1", "provider-b", "provider-a")).thenReturn(true);
        MeetingAdmissionEntity decided = meetingService.admit(host, convId, "provider-b");
        assertThat(decided.getStatus()).isEqualTo(MeetingAdmissionEntity.STATUS_ADMITTED);
        assertThat(decided.getDecidedBy()).isEqualTo("provider-a");
        verify(rtcGateway).admit("rtc-1", "provider-b", "provider-a");
        assertThat(outbox.findAll())
                .anyMatch(e -> e.getEventType().equals("impilo.khuluma.meeting.admission-admitted.v1"));

        // Re-join now mints the token.
        when(rtcGateway.issueToken(eq("rtc-1"), eq("provider-b"), anyString(), eq("PARTICIPANT")))
                .thenReturn(ready("member-jwt"));
        MeetingJoin joined = meetingService.join(member, convId);
        assertThat(joined.status()).isEqualTo(MeetingJoin.STATUS_READY);
        assertThat(joined.accessToken()).isEqualTo("member-jwt");
    }

    @Test
    void deny_mirrorsDecision_andMemberSeesDenied() {
        UUID tenant = UUID.randomUUID();
        ActorContext host = ctx(tenant, "provider-a");
        ActorContext member = ctx(tenant, "provider-b");
        MeetingResult meeting = meetingWith(host, "provider-b");
        UUID convId = UUID.fromString(meeting.conversationId());
        stubRoom("ev-1", "rtc-1");
        when(rtcGateway.issueToken(eq("rtc-1"), eq("provider-b"), anyString(), eq("PARTICIPANT")))
                .thenReturn(waiting());
        meetingService.join(member, convId);

        when(rtcGateway.deny("rtc-1", "provider-b", "not invited")).thenReturn(true);
        MeetingAdmissionEntity denied = meetingService.deny(host, convId, "provider-b", "not invited");
        assertThat(denied.getStatus()).isEqualTo(MeetingAdmissionEntity.STATUS_DENIED);
        assertThat(denied.getDeniedReason()).isEqualTo("not invited");

        RtcGatewayClient.RtcSessionResult deniedResult =
                new RtcGatewayClient.RtcSessionResult(true, "rtc-1", null, null, null, null, "DENIED", null);
        when(rtcGateway.issueToken(eq("rtc-1"), eq("provider-b"), anyString(), eq("PARTICIPANT")))
                .thenReturn(deniedResult);
        assertThat(meetingService.join(member, convId).status()).isEqualTo(MeetingJoin.STATUS_DENIED);
    }

    @Test
    void lobbyModeration_isHostOrCohostOnly() {
        UUID tenant = UUID.randomUUID();
        ActorContext host = ctx(tenant, "provider-a");
        ActorContext member = ctx(tenant, "provider-b");
        MeetingResult meeting = meetingWith(host, "provider-b");
        UUID convId = UUID.fromString(meeting.conversationId());

        assertThatThrownBy(() -> meetingService.lobby(member, convId)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> meetingService.admit(member, convId, "provider-x"))
                .isInstanceOf(SecurityException.class);
        assertThat(meetingService.lobby(host, convId)).isEmpty();
    }

    @Test
    void cohost_assignment_grantsCohostGrant_andModerationRights() {
        UUID tenant = UUID.randomUUID();
        ActorContext host = ctx(tenant, "provider-a");
        ActorContext cohost = ctx(tenant, "provider-b");
        MeetingResult meeting = meetingWith(host, "provider-b");
        UUID convId = UUID.fromString(meeting.conversationId());

        // Only the host can assign.
        assertThatThrownBy(() -> meetingService.setCohost(cohost, convId, "provider-b", true))
                .isInstanceOf(SecurityException.class);
        meetingService.setCohost(host, convId, "provider-b", true);
        assertThat(outbox.findAll())
                .anyMatch(e -> e.getEventType().equals("impilo.khuluma.meeting.cohost-assigned.v1"));

        // Cohost joins with the COHOST template role (lobby bypass on the gateway side).
        stubRoom("ev-1", "rtc-1");
        when(rtcGateway.issueToken(eq("rtc-1"), eq("provider-b"), anyString(), eq("COHOST")))
                .thenReturn(ready("cohost-jwt"));
        MeetingJoin join = meetingService.join(cohost, convId);
        assertThat(join.role()).isEqualTo("COHOST");
        assertThat(join.accessToken()).isEqualTo("cohost-jwt");

        // And can now moderate the lobby.
        assertThat(meetingService.lobby(cohost, convId)).isNotEmpty();

        // Revoke drops back to PARTICIPANT.
        meetingService.setCohost(host, convId, "provider-b", false);
        when(rtcGateway.issueToken(eq("rtc-1"), eq("provider-b"), anyString(), eq("PARTICIPANT")))
                .thenReturn(waiting());
        assertThat(meetingService.join(cohost, convId).role()).isEqualTo("PARTICIPANT");
    }

    @Test
    void raiseHand_andReactions_requireMembership_andAudit() {
        UUID tenant = UUID.randomUUID();
        ActorContext host = ctx(tenant, "provider-a");
        ActorContext stranger = ctx(tenant, "provider-x");
        MeetingResult meeting = meetingWith(host, "provider-b");
        UUID convId = UUID.fromString(meeting.conversationId());

        meetingService.raiseHand(host, convId, true);
        meetingService.raiseHand(host, convId, false);
        meetingService.react(host, convId, "👏");
        assertThat(outbox.findAll())
                .anyMatch(e -> e.getEventType().equals("impilo.khuluma.meeting.hand-raised.v1"));
        assertThat(outbox.findAll())
                .anyMatch(e -> e.getEventType().equals("impilo.khuluma.meeting.hand-lowered.v1"));
        assertThat(outbox.findAll())
                .anyMatch(e -> e.getEventType().equals("impilo.khuluma.meeting.reaction.v1"));

        assertThatThrownBy(() -> meetingService.raiseHand(stranger, convId, true))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> meetingService.react(stranger, convId, "👏"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> meetingService.react(host, convId, "x".repeat(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actionItems_crudLifecycle_withMembershipGate() {
        UUID tenant = UUID.randomUUID();
        ActorContext host = ctx(tenant, "provider-a");
        ActorContext stranger = ctx(tenant, "provider-x");
        MeetingResult meeting = meetingWith(host, "provider-b");
        UUID convId = UUID.fromString(meeting.conversationId());

        MeetingActionItemEntity item = meetingService.createActionItem(host, convId,
                "Share protocol update", "provider-b", "Dr B", java.time.LocalDate.parse("2026-07-15"));
        assertThat(item.getStatus()).isEqualTo("OPEN");
        assertThat(meetingService.listActionItems(host, convId)).hasSize(1);

        MeetingActionItemEntity done = meetingService.updateActionItem(host, convId, item.getActionItemId(),
                "DONE", null, null, null, null);
        assertThat(done.getStatus()).isEqualTo("DONE");
        assertThat(done.getAssigneeId()).isEqualTo("provider-b");

        assertThatThrownBy(() -> meetingService.createActionItem(stranger, convId, "sneak", null, null, null))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> meetingService.updateActionItem(host, convId, item.getActionItemId(),
                "BOGUS", null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(outbox.findAll())
                .anyMatch(e -> e.getEventType().equals("impilo.khuluma.meeting.action-item-created.v1"));
    }

    @Test
    void inviteLink_mintByHost_resolveJoinsAuthenticatedActor() {
        UUID tenant = UUID.randomUUID();
        ActorContext host = ctx(tenant, "provider-a");
        ActorContext invitee = ctx(tenant, "provider-z");
        MeetingResult meeting = meetingWith(host, "provider-b");
        UUID convId = UUID.fromString(meeting.conversationId());

        // Members cannot mint; hosts can.
        ActorContext member = ctx(tenant, "provider-b");
        assertThatThrownBy(() -> meetingService.createInvite(member, convId, null, null))
                .isInstanceOf(SecurityException.class);
        var invite = meetingService.createInvite(host, convId, null, 3600L);
        assertThat(invite.token()).contains(".");
        assertThat(invite.role()).isEqualTo("MEMBER");

        // A stranger with the link resolves it and becomes a participant, able to join (knock).
        MeetingResult resolved = meetingService.resolveInvite(invitee, invite.token());
        assertThat(resolved.conversationId()).isEqualTo(meeting.conversationId());
        assertThat(conversationService.isActiveParticipant(convId, "provider-z")).isTrue();
        assertThat(outbox.findAll())
                .anyMatch(e -> e.getEventType().equals("impilo.khuluma.meeting.invite-resolved.v1"));

        // Forged/cross-tenant tokens are refused.
        assertThatThrownBy(() -> meetingService.resolveInvite(invitee, invite.token() + "x"))
                .isInstanceOf(IllegalArgumentException.class);
        ActorContext otherTenant = ctx(UUID.randomUUID(), "provider-q");
        assertThatThrownBy(() -> meetingService.resolveInvite(otherTenant, invite.token()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detail_surfacesScheduleCohostsAndAttendance() {
        UUID tenant = UUID.randomUUID();
        ActorContext host = ctx(tenant, "provider-a");
        MeetingResult meeting = meetingWith(host, "provider-b");
        UUID convId = UUID.fromString(meeting.conversationId());
        meetingService.setCohost(host, convId, "provider-b", true);
        when(liveService.getEvent("ev-1")).thenReturn(new LiveServiceClient.EventResult(
                true, "ev-1", "Case review", "SCHEDULED", "2026-07-10T09:00:00Z", null, null));
        stubRoom("ev-1", "rtc-1");
        when(rtcGateway.issueToken(eq("rtc-1"), eq("provider-a"), anyString(), eq("HOST")))
                .thenReturn(ready("host-jwt"));
        meetingService.join(host, convId);

        MeetingService.MeetingDetail detail = meetingService.detail(host, convId);
        assertThat(detail.eventId()).isEqualTo("ev-1");
        assertThat(detail.scheduledAt()).isEqualTo("2026-07-10T09:00:00Z");
        assertThat(detail.hostId()).isEqualTo("provider-a");
        assertThat(detail.cohostIds()).containsExactly("provider-b");
        assertThat(detail.attendance()).hasSize(1);
    }

    @Test
    void join_fallsBackToLiveServiceToken_whenNoRtcSessionId() {
        UUID tenant = UUID.randomUUID();
        ActorContext host = ctx(tenant, "provider-a");
        MeetingResult meeting = meetingWith(host, "provider-b");
        UUID convId = UUID.fromString(meeting.conversationId());
        when(liveService.joinRoom(eq("ev-1"), anyString(), anyString(), anyString()))
                .thenReturn(LiveServiceClient.JoinRoomResult.unavailable("live down"));
        when(liveService.issueToken(eq("ev-1"), eq("provider-a"), anyString()))
                .thenReturn(new LiveServiceClient.RoomTokenResult(true, "room-1", "ws://livekit:7880",
                        "legacy-jwt", "RTC_GATEWAY", "live", null));

        MeetingJoin join = meetingService.join(host, convId);
        assertThat(join.status()).isEqualTo(MeetingJoin.STATUS_READY);
        assertThat(join.accessToken()).isEqualTo("legacy-jwt");
        assertThat(join.rtcSessionId()).isNull();
    }

    @Test
    void join_requiresMembership() {
        UUID tenant = UUID.randomUUID();
        ActorContext host = ctx(tenant, "provider-a");
        ActorContext stranger = ctx(tenant, "provider-x");
        MeetingResult meeting = meetingWith(host, "provider-b");
        assertThatThrownBy(() -> meetingService.join(stranger, UUID.fromString(meeting.conversationId())))
                .isInstanceOf(SecurityException.class);
    }
}
