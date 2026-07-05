package zw.gov.mohcc.impilo.learning.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.learning.fundo.FundoReplayMediaService;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaAssetEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.ScheduledLearningSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the replay-published consumer (W4): envelope unwrap,
 * linked-session gating (layering law), no-artifact skip, and poison-message
 * swallowing — the estate consumer convention.
 */
@ExtendWith(MockitoExtension.class)
class LiveReplayConsumerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LIVE_EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID COURSE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String OBJECT_ID = "55555555-5555-5555-5555-555555555555";

    @Mock private ScheduledLearningSessionRepository sessionRepository;
    @Mock private FundoReplayMediaService replayMediaService;

    private LiveReplayConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new LiveReplayConsumer(new ObjectMapper(), sessionRepository, replayMediaService);
        MediaAssetEntity adopted = new MediaAssetEntity();
        adopted.setId(UUID.randomUUID());
        lenient().when(replayMediaService.adoptReplay(any(), any())).thenReturn(adopted);
    }

    @Test
    void replayPublished_adoptsArtifactForLinkedSession() {
        ScheduledLearningSessionEntity session = linkedSession();
        when(sessionRepository.findFirstByLiveEventIdOrderByCreatedAtDesc(LIVE_EVENT_ID))
                .thenReturn(Optional.of(session));

        consumer.onReplayPublished(replayPayload(OBJECT_ID, 1800));

        ArgumentCaptor<FundoReplayMediaService.ReplayPublished> captor =
                ArgumentCaptor.forClass(FundoReplayMediaService.ReplayPublished.class);
        verify(replayMediaService).adoptReplay(eq(session), captor.capture());
        FundoReplayMediaService.ReplayPublished replay = captor.getValue();
        assertThat(replay.liveEventId()).isEqualTo(LIVE_EVENT_ID);
        assertThat(replay.documentObjectId()).isEqualTo(OBJECT_ID);
        assertThat(replay.durationSeconds()).isEqualTo(1800);
        assertThat(replay.title()).isEqualTo("Cold chain webinar");
    }

    @Test
    void replayPublished_ignoresUnlinkedLiveEvents() {
        when(sessionRepository.findFirstByLiveEventIdOrderByCreatedAtDesc(LIVE_EVENT_ID))
                .thenReturn(Optional.empty());

        consumer.onReplayPublished(replayPayload(OBJECT_ID, 1800));

        verifyNoInteractions(replayMediaService);
    }

    @Test
    void replayPublished_skipsPayloadsWithoutDocumentObjectId() {
        // Legacy LiveKit-room replays carry no catalogued artifact — nothing to adopt.
        consumer.onReplayPublished(replayPayload(null, null));

        verifyNoInteractions(sessionRepository, replayMediaService);
    }

    @Test
    void malformedAndFailingMessagesAreSwallowed() {
        assertThatCode(() -> consumer.onReplayPublished("not json")).doesNotThrowAnyException();
        assertThatCode(() -> consumer.onReplayPublished("{}")).doesNotThrowAnyException();

        when(sessionRepository.findFirstByLiveEventIdOrderByCreatedAtDesc(LIVE_EVENT_ID))
                .thenReturn(Optional.of(linkedSession()));
        when(replayMediaService.adoptReplay(any(), any())).thenThrow(new IllegalStateException("boom"));
        assertThatCode(() -> consumer.onReplayPublished(replayPayload(OBJECT_ID, 10)))
                .doesNotThrowAnyException();
    }

    @Test
    void flatPayloadWithoutEnvelopeIsTolerated() {
        when(sessionRepository.findFirstByLiveEventIdOrderByCreatedAtDesc(LIVE_EVENT_ID))
                .thenReturn(Optional.of(linkedSession()));

        consumer.onReplayPublished("{\"eventId\":\"" + LIVE_EVENT_ID + "\","
                + "\"documentObjectId\":\"" + OBJECT_ID + "\"}");

        verify(replayMediaService).adoptReplay(any(), any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ScheduledLearningSessionEntity linkedSession() {
        ScheduledLearningSessionEntity s = new ScheduledLearningSessionEntity();
        s.setId(SESSION_ID);
        s.setTenantId(TENANT);
        s.setCourseId(COURSE_ID);
        s.setSessionMode("LIVE");
        s.setLiveEventId(LIVE_EVENT_ID);
        return s;
    }

    /** Mirrors LiveEventEmitter's envelope: {eventType, aggregateType, …, payload:{…}}. */
    private static String replayPayload(String documentObjectId, Integer durationSeconds) {
        StringBuilder payload = new StringBuilder("{")
                .append("\"eventId\":\"").append(LIVE_EVENT_ID).append("\",")
                .append("\"title\":\"Cold chain webinar\",")
                .append("\"status\":\"PUBLISHED_REPLAY\",")
                .append("\"contextType\":\"FUNDO_WEBINAR\",")
                .append("\"sessionId\":\"").append(UUID.randomUUID()).append("\"");
        if (documentObjectId != null) {
            payload.append(",\"documentObjectId\":\"").append(documentObjectId).append("\"");
        }
        if (durationSeconds != null) {
            payload.append(",\"durationSeconds\":").append(durationSeconds);
        }
        payload.append("}");
        return "{"
                + "\"eventType\":\"impilo.live.replay.published.v1\","
                + "\"aggregateType\":\"LIVE_EVENT\","
                + "\"aggregateId\":\"" + LIVE_EVENT_ID + "\","
                + "\"occurredAt\":\"2026-07-05T10:00:00Z\","
                + "\"payload\":" + payload
                + "}";
    }
}
