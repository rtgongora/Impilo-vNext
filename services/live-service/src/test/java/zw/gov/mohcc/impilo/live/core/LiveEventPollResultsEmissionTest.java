package zw.gov.mohcc.impilo.live.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventPollEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventPollResponseEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventPollRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventPollResponseRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRoleAssignmentRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W3: {@code impilo.live.poll.results.v1} is appended to the outbox when an event
 * ends and had polls; poll failures never block the ENDED transition.
 */
@ExtendWith(MockitoExtension.class)
class LiveEventPollResultsEmissionTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private LiveEventRepository eventRepository;
    @Mock private LiveEventRoleAssignmentRepository roleRepository;
    @Mock private LiveEventStateMachine stateMachine;
    @Mock private LiveEventEmitter emitter;
    @Mock private LiveIntegrationOrchestrator integrationOrchestrator;
    @Mock private LiveGovernanceGuard governanceGuard;
    @Mock private LiveEventPollRepository pollRepository;
    @Mock private LiveEventPollResponseRepository pollResponseRepository;

    private LiveEventService service;
    private LiveEventEntity event;

    @BeforeEach
    void setUp() {
        service = new LiveEventService(eventRepository, roleRepository, stateMachine, emitter,
                integrationOrchestrator, governanceGuard, pollRepository, pollResponseRepository,
                new ObjectMapper());
        event = new LiveEventEntity();
        event.setId(EVENT_ID);
        event.setTenantId(TENANT_ID);
        event.setTitle("CPD Webinar");
        event.setStatus("LIVE");
        when(eventRepository.findByIdAndTenantId(EVENT_ID, TENANT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void end_emitsAggregatedPollResults() {
        LiveEventPollEntity poll = new LiveEventPollEntity();
        UUID pollId = UUID.randomUUID();
        poll.setId(pollId);
        poll.setEventId(EVENT_ID);
        poll.setQuestion("Was this useful?");
        poll.setOptions("[\"yes\",\"no\"]");
        when(pollRepository.findByEventIdOrderByCreatedAtDesc(EVENT_ID)).thenReturn(List.of(poll));
        when(pollResponseRepository.findByPollId(pollId)).thenReturn(List.of(
                response(pollId, "p1", "yes"),
                response(pollId, "p2", "yes"),
                response(pollId, "p3", "no")));

        service.end(TENANT_ID, EVENT_ID, "moderator");

        ArgumentCaptor<Map<String, Object>> payload =
                ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(emitter).emit(eq(TENANT_ID), eq("LIVE_EVENT"), eq(EVENT_ID.toString()),
                eq("impilo.live.poll.results.v1"), eq("LIVE_EVENT"), eq(EVENT_ID.toString()),
                payload.capture());
        List<Map<String, Object>> polls = (List<Map<String, Object>>) payload.getValue().get("polls");
        assertThat(polls).hasSize(1);
        assertThat(polls.get(0)).containsEntry("pollId", pollId.toString())
                .containsEntry("question", "Was this useful?")
                .containsEntry("totalResponses", 3);
        Map<String, Integer> counts = (Map<String, Integer>) polls.get(0).get("counts");
        assertThat(counts).containsEntry("yes", 2).containsEntry("no", 1);
    }

    @Test
    void end_withoutPollsEmitsNoPollResults() {
        when(pollRepository.findByEventIdOrderByCreatedAtDesc(EVENT_ID)).thenReturn(List.of());

        service.end(TENANT_ID, EVENT_ID, "moderator");

        verify(emitter).emit(eq(TENANT_ID), anyString(), anyString(),
                eq("impilo.live.event.ended.v1"), anyString(), anyString(), any());
        verify(emitter, never()).emit(any(), anyString(), anyString(),
                eq("impilo.live.poll.results.v1"), anyString(), anyString(), any());
    }

    @Test
    void end_pollFailureNeverBlocksEndedTransition() {
        when(pollRepository.findByEventIdOrderByCreatedAtDesc(EVENT_ID))
                .thenThrow(new IllegalStateException("poll table unavailable"));

        assertThatCode(() -> service.end(TENANT_ID, EVENT_ID, "moderator"))
                .doesNotThrowAnyException();
        verify(emitter).emit(eq(TENANT_ID), anyString(), anyString(),
                eq("impilo.live.event.ended.v1"), anyString(), anyString(), any());
    }

    private static LiveEventPollResponseEntity response(UUID pollId, String participant, String option) {
        LiveEventPollResponseEntity r = new LiveEventPollResponseEntity();
        r.setId(UUID.randomUUID());
        r.setPollId(pollId);
        r.setParticipantId(participant);
        r.setSelectedOption(option);
        return r;
    }
}
