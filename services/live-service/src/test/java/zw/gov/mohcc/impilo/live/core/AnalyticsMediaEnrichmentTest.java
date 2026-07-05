package zw.gov.mohcc.impilo.live.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.live.integration.RtcGatewayClient;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventAnalyticsSnapshotEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventSessionEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/** Analytics snapshots enrich with media-quality aggregates from rtc-gateway's stats API. */
@ExtendWith(MockitoExtension.class)
class AnalyticsMediaEnrichmentTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private LiveEventService eventService;
    @Mock private LiveEventAnalyticsSnapshotRepository snapshotRepository;
    @Mock private LiveEventRegistrationRepository registrationRepository;
    @Mock private LiveEventAttendanceRepository attendanceRepository;
    @Mock private LiveEventChatMessageRepository chatRepository;
    @Mock private LiveEventQuestionRepository questionRepository;
    @Mock private LiveEventPollRepository pollRepository;
    @Mock private LiveEventFeedbackRepository feedbackRepository;
    @Mock private LiveEventSessionRepository sessionRepository;
    @Mock private RtcGatewayClient rtcGatewayClient;

    private AnalyticsService analyticsService;
    private LiveEventEntity event;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(eventService, snapshotRepository, registrationRepository,
                attendanceRepository, chatRepository, questionRepository, pollRepository, feedbackRepository,
                sessionRepository, rtcGatewayClient);
        event = new LiveEventEntity();
        event.setId(EVENT_ID);
        event.setTenantId(TENANT_ID);
        when(eventService.get(TENANT_ID, EVENT_ID)).thenReturn(event);
        lenient().when(attendanceRepository.findByEventId(EVENT_ID)).thenReturn(List.of());
        lenient().when(chatRepository.findByEventIdOrderByCreatedAtAsc(EVENT_ID)).thenReturn(List.of());
        lenient().when(questionRepository.findByEventIdOrderByPinnedDescUpvotesDescCreatedAtAsc(EVENT_ID))
                .thenReturn(List.of());
        lenient().when(pollRepository.findByEventIdOrderByCreatedAtDesc(EVENT_ID)).thenReturn(List.of());
        lenient().when(feedbackRepository.findByEventId(EVENT_ID)).thenReturn(List.of());
        lenient().when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void captureSnapshot_persistsMediaMetricsFromRtcStats() {
        when(sessionRepository.findByEventIdOrderByCreatedAtDesc(EVENT_ID))
                .thenReturn(List.of(session("rtc-main-1")));
        when(rtcGatewayClient.getSessionStats(any(), anyString())).thenReturn(Map.of(
                "data", Map.of(
                        "distinctParticipants", 42,
                        "avgDurationSeconds", 812.5,
                        "abnormalDisconnects", 3,
                        "peakParticipants", 40,
                        "disconnectReasons", Map.of("SIGNAL_CLOSE", 3))));

        analyticsService.captureSnapshot(TENANT_ID, EVENT_ID);

        ArgumentCaptor<LiveEventAnalyticsSnapshotEntity> snaps =
                ArgumentCaptor.forClass(LiveEventAnalyticsSnapshotEntity.class);
        verify(snapshotRepository, org.mockito.Mockito.atLeastOnce()).save(snaps.capture());
        Map<String, Double> byName = snaps.getAllValues().stream()
                .collect(java.util.stream.Collectors.toMap(
                        LiveEventAnalyticsSnapshotEntity::getMetricName,
                        s -> s.getMetricValue().doubleValue(),
                        (a, b) -> b));
        assertThat(byName).containsEntry("media_distinct_participants", 42.0);
        assertThat(byName).containsEntry("media_avg_duration_seconds", 812.5);
        assertThat(byName).containsEntry("media_abnormal_disconnects", 3.0);
        assertThat(byName).containsEntry("media_peak_participants", 40.0);
        // non-numeric stats (maps) are never persisted as metrics
        assertThat(byName).doesNotContainKey("disconnectReasons");
    }

    @Test
    void captureSnapshot_degradesGracefullyWithoutARoom() {
        when(sessionRepository.findByEventIdOrderByCreatedAtDesc(EVENT_ID)).thenReturn(List.of());

        analyticsService.captureSnapshot(TENANT_ID, EVENT_ID);

        verify(rtcGatewayClient, never()).getSessionStats(any(), anyString());
    }

    @Test
    void captureSnapshot_survivesGatewayFailure() {
        when(sessionRepository.findByEventIdOrderByCreatedAtDesc(EVENT_ID))
                .thenReturn(List.of(session("rtc-main-1")));
        when(rtcGatewayClient.getSessionStats(any(), anyString()))
                .thenThrow(new IllegalStateException("gateway down"));

        // must not throw — engagement metrics alone are still captured
        analyticsService.captureSnapshot(TENANT_ID, EVENT_ID);
    }

    private static LiveEventSessionEntity session(String roomId) {
        LiveEventSessionEntity session = new LiveEventSessionEntity();
        session.setId(UUID.randomUUID());
        session.setEventId(EVENT_ID);
        session.setProviderRoomId(roomId);
        return session;
    }
}
