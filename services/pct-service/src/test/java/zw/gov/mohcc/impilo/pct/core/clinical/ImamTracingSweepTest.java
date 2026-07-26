package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImamEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ImamEpisodeRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sweep exists because the event that fires when someone records a missed review does not fire
 * for the child nobody records anything about — which is precisely the child at risk.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImamTracingSweepTest {

    @Mock private ImamEpisodeRepository episodeRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ImamTracingSweep sweep;

    @BeforeEach
    void setUp() {
        sweep = new ImamTracingSweep(episodeRepository, outboxRepository, new ObjectMapper(), true);
        when(episodeRepository.save(any(ImamEpisodeEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void aChildWhoSimplyStoppedComingRaisesATracingEventWithoutAnyoneRecordingTheAbsence() {
        ImamEpisodeEntity episode = episode(LocalDate.parse("2026-06-08"));
        when(episodeRepository.findAllOverdue(any())).thenReturn(List.of(episode));

        int raised = sweep.raiseTracingEvents(LocalDate.parse("2026-06-19"));

        assertThat(raised).isEqualTo(1);
        ArgumentCaptor<EventOutboxEntity> event = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("IMAM_TRACING_REQUIRED");
        assertThat(event.getValue().getPayload()).contains("TRACE_REQUIRED", "\"detectedBy\":\"ELAPSED_TIME\"");
        assertThat(episode.getTracingNotifiedMissedVisits()).isEqualTo(1);
    }

    @Test
    void theSameCrossingIsNotRaisedAgainTheFollowingNight() {
        // An alert repeated nightly is an alert a team stops reading, and then the one that matters
        // arrives in a pile of identical ones.
        ImamEpisodeEntity episode = episode(LocalDate.parse("2026-06-08"));
        episode.setTracingNotifiedMissedVisits(1);
        when(episodeRepository.findAllOverdue(any())).thenReturn(List.of(episode));

        int raised = sweep.raiseTracingEvents(LocalDate.parse("2026-06-20"));

        assertThat(raised).isZero();
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void crossingFromTracingIntoDefaultingRaisesASecondEventBecauseItIsNewInformation() {
        ImamEpisodeEntity episode = episode(LocalDate.parse("2026-06-08"));
        episode.setTracingNotifiedMissedVisits(1);
        when(episodeRepository.findAllOverdue(any())).thenReturn(List.of(episode));

        int raised = sweep.raiseTracingEvents(LocalDate.parse("2026-06-26"));

        assertThat(raised).isEqualTo(1);
        ArgumentCaptor<EventOutboxEntity> event = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(event.capture());
        assertThat(event.getValue().getPayload()).contains("DEFAULTER");
        assertThat(episode.getTracingNotifiedMissedVisits()).isEqualTo(2);
    }

    @Test
    void aChildEnrolledAndNeverReviewedIsMarkedAsSuchOnTheEvent() {
        ImamEpisodeEntity episode = episode(null);
        when(episodeRepository.findAllOverdue(any())).thenReturn(List.of(episode));

        sweep.raiseTracingEvents(LocalDate.parse("2026-06-26"));

        ArgumentCaptor<EventOutboxEntity> event = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(event.capture());
        assertThat(event.getValue().getPayload()).contains("\"neverReviewed\":true");
    }

    @Test
    void aChildStillWithinTheGraceWindowRaisesNothing() {
        ImamEpisodeEntity episode = episode(LocalDate.parse("2026-06-08"));
        when(episodeRepository.findAllOverdue(any())).thenReturn(List.of(episode));

        assertThat(sweep.raiseTracingEvents(LocalDate.parse("2026-06-16"))).isZero();
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void aDisabledSweepDoesNothingAtAll() {
        ImamTracingSweep disabled = new ImamTracingSweep(
                episodeRepository, outboxRepository, new ObjectMapper(), false);

        disabled.sweep();

        verify(outboxRepository, never()).save(any());
    }

    private static ImamEpisodeEntity episode(LocalDate lastContactOn) {
        ImamEpisodeEntity e = new ImamEpisodeEntity();
        e.setImamEpisodeId(UUID.randomUUID());
        e.setTenantId(UUID.randomUUID());
        e.setSubjectCpid("CPID-CHILD-TRACE");
        e.setProgramme("OTP");
        e.setStatus("ACTIVE");
        e.setEnrolledAt(OffsetDateTime.parse("2026-06-01T09:00:00Z"));
        e.setEnrolledBy("nurse-1");
        e.setReviewIntervalDays(7);
        e.setAttendanceGraceDays(2);
        e.setTraceAfterMissedVisits(1);
        e.setDefaulterMissedVisits(2);
        e.setLastContactOn(lastContactOn);
        e.setNextReviewDue(lastContactOn == null
                ? LocalDate.parse("2026-06-08") : lastContactOn.plusDays(7));
        return e;
    }
}
