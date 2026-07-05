package zw.gov.mohcc.impilo.live.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.media.LiveMediaProvider;
import zw.gov.mohcc.impilo.live.media.MediaRoomContext;
import zw.gov.mohcc.impilo.live.media.MediaTokenResult;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventSessionEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventSessionRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LIVE_EVENT polish behaviours on LiveRoomService: server-side role
 * enforcement on token minting, event capacity carried to the media
 * provider, and the backstage child-room lifecycle.
 */
@ExtendWith(MockitoExtension.class)
class LiveRoomServiceStageTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock private LiveEventService eventService;
    @Mock private AttendanceService attendanceService;
    @Mock private LiveEventSessionRepository sessionRepository;
    @Mock private LiveMediaProvider mediaProvider;
    @Mock private ReplayService replayService;
    @Mock private LiveGovernanceGuard governanceGuard;
    @Mock private StageService stageService;

    private LiveRoomService roomService;
    private LiveEventEntity event;
    private LiveEventSessionEntity session;

    @BeforeEach
    void setUp() {
        roomService = new LiveRoomService(eventService, attendanceService, sessionRepository,
                mediaProvider, replayService, governanceGuard, stageService);
        event = new LiveEventEntity();
        event.setId(EVENT_ID);
        event.setTenantId(TENANT_ID);
        event.setMode("PUBLIC_BROADCAST");
        event.setStatus(LiveEventStatus.LIVE.name());
        event.setMaxParticipants(150);
        session = new LiveEventSessionEntity();
        session.setId(SESSION_ID);
        session.setEventId(EVENT_ID);
        session.setProviderType("RTC_GATEWAY");
        session.setProviderRoomId("rtc-main-1");
        lenient().when(eventService.get(TENANT_ID, EVENT_ID)).thenReturn(event);
        lenient().when(sessionRepository.findFirstByEventIdAndEndedAtIsNullOrderByCreatedAtDesc(EVENT_ID))
                .thenReturn(Optional.of(session));
        lenient().when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(mediaProvider.issueToken(any())).thenReturn(token());
        lenient().when(mediaProvider.providerType()).thenReturn("RTC_GATEWAY");
    }

    @Test
    void token_mintsAgainstTheServerResolvedRole() {
        when(stageService.resolveEffectiveRole(event, "citizen-9", "PRESENTER")).thenReturn("AUDIENCE");

        LiveRoomService.RoomToken result = roomService.token(TENANT_ID, EVENT_ID, "citizen-9", "PRESENTER");

        assertThat(result.role()).isEqualTo("AUDIENCE");
        ArgumentCaptor<MediaRoomContext> ctx = ArgumentCaptor.forClass(MediaRoomContext.class);
        verify(mediaProvider).issueToken(ctx.capture());
        assertThat(ctx.getValue().participantRole()).isEqualTo("AUDIENCE");
        // event capacity + session mode ride the provider attributes
        assertThat(ctx.getValue().attributes()).containsEntry("maxParticipants", 150);
        assertThat(ctx.getValue().attributes()).containsEntry("sessionMode", "LIVE_EVENT");
    }

    @Test
    void joinBackstage_requiresStageGrant() {
        when(stageService.resolveTier(event, "citizen-9")).thenReturn(StageService.StageTier.AUDIENCE);

        assertThatThrownBy(() -> roomService.joinBackstage(TENANT_ID, EVENT_ID, "citizen-9", "CITIZEN"))
                .isInstanceOf(LiveGovernanceException.class)
                .hasMessageContaining("Backstage");
    }

    @Test
    void joinBackstage_provisionsSuffixedChildRoomLinkedToMain() {
        when(stageService.resolveTier(event, "producer-1")).thenReturn(StageService.StageTier.PRODUCER);
        when(mediaProvider.provisionRoom(any())).thenAnswer(inv -> {
            MediaRoomContext ctx = inv.getArgument(0);
            return new MediaRoomContext(ctx.tenantId(), ctx.eventId(), ctx.sessionId(),
                    ctx.participantId(), ctx.participantRole(), ctx.facilityId(),
                    "rtc-main-1-backstage", "RTC_GATEWAY", ctx.attributes());
        });

        LiveEventSessionEntity result = roomService.joinBackstage(TENANT_ID, EVENT_ID, "producer-1", "PROVIDER");

        assertThat(result.getBackstageRoomId()).isEqualTo("rtc-main-1-backstage");
        ArgumentCaptor<MediaRoomContext> ctx = ArgumentCaptor.forClass(MediaRoomContext.class);
        verify(mediaProvider).provisionRoom(ctx.capture());
        assertThat(ctx.getValue().attributes()).containsEntry("sessionIdSuffix", "-backstage");
        assertThat(ctx.getValue().attributes()).containsEntry("parentSessionId", "rtc-main-1");
    }

    @Test
    void joinBackstage_isIdempotentOnceProvisioned() {
        session.setBackstageRoomId("rtc-main-1-backstage");
        when(stageService.resolveTier(event, "producer-1")).thenReturn(StageService.StageTier.HOST);

        LiveEventSessionEntity result = roomService.joinBackstage(TENANT_ID, EVENT_ID, "producer-1", "PROVIDER");

        assertThat(result.getBackstageRoomId()).isEqualTo("rtc-main-1-backstage");
        verify(mediaProvider, org.mockito.Mockito.never()).provisionRoom(any());
    }

    @Test
    void backstageToken_mintsForTheBackstageRoomWithTierRole() {
        session.setBackstageRoomId("rtc-main-1-backstage");
        when(stageService.resolveTier(event, "speaker-2")).thenReturn(StageService.StageTier.SPEAKER);

        LiveRoomService.RoomToken result = roomService.backstageToken(TENANT_ID, EVENT_ID, "speaker-2");

        assertThat(result.role()).isEqualTo("SPEAKER");
        ArgumentCaptor<MediaRoomContext> ctx = ArgumentCaptor.forClass(MediaRoomContext.class);
        verify(mediaProvider).issueToken(ctx.capture());
        assertThat(ctx.getValue().providerRoomId()).isEqualTo("rtc-main-1-backstage");
    }

    @Test
    void backstageToken_failsWhenNoBackstageRoomYet() {
        when(stageService.resolveTier(event, "speaker-2")).thenReturn(StageService.StageTier.SPEAKER);

        assertThatThrownBy(() -> roomService.backstageToken(TENANT_ID, EVENT_ID, "speaker-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("backstage");
    }

    @Test
    void join_resolvesRoleBeforeGovernanceAndAttendance() {
        when(stageService.resolveEffectiveRole(event, "citizen-9", "SPEAKER")).thenReturn("AUDIENCE");

        roomService.join(TENANT_ID, EVENT_ID, "citizen-9", "CITIZEN", "SPEAKER", false);

        verify(governanceGuard).validateJoin(event, "AUDIENCE", "CITIZEN", false);
        verify(attendanceService).join(TENANT_ID, EVENT_ID, "citizen-9", "CITIZEN");
    }

    private static MediaTokenResult token() {
        return new MediaTokenResult("rtc-main-1", "wss://livekit.test", "token",
                Instant.now().plusSeconds(3600), "RTC_GATEWAY", "live");
    }
}
