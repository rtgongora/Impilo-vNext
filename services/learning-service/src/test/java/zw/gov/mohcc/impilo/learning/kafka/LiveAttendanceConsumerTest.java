package zw.gov.mohcc.impilo.learning.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.learning.fundo.FundoLiveCompletionService;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.SessionAttendanceEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.ScheduledLearningSessionRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.SessionAttendanceRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the live media-truth consumer (W3): envelope unwrap, webhook-accurate
 * attendance upsert (idempotent absolute minutes), event-ended completion pass, and
 * poison-message swallowing — the varapi estate consumer convention.
 */
@ExtendWith(MockitoExtension.class)
class LiveAttendanceConsumerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LIVE_EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID COURSE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock private ScheduledLearningSessionRepository sessionRepository;
    @Mock private SessionAttendanceRepository attendanceRepository;
    @Mock private EnrolmentRepository enrolmentRepository;
    @Mock private FundoLiveCompletionService liveCompletionService;

    private LiveAttendanceConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new LiveAttendanceConsumer(new ObjectMapper(), sessionRepository,
                attendanceRepository, enrolmentRepository, liveCompletionService);
    }

    // ── attendance.updated ────────────────────────────────────────────────────

    @Test
    void attendanceUpdated_upsertsWebhookAccurateMinutesOntoLinkedSession() {
        ScheduledLearningSessionEntity session = linkedSession();
        EnrolmentEntity enrolment = enrolment("provider-9", "IN_PROGRESS");
        when(sessionRepository.findFirstByLiveEventIdOrderByCreatedAtDesc(LIVE_EVENT_ID))
                .thenReturn(Optional.of(session));
        when(enrolmentRepository.findByTenantIdAndCourseId(TENANT, COURSE_ID))
                .thenReturn(List.of(enrolment));
        when(attendanceRepository.findBySessionIdAndSubjectTypeAndSubjectId(SESSION_ID, "PROVIDER", "provider-9"))
                .thenReturn(Optional.empty());
        when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onAttendanceUpdated(attendancePayload("provider-9", 42, 50));

        ArgumentCaptor<SessionAttendanceEntity> saved = ArgumentCaptor.forClass(SessionAttendanceEntity.class);
        verify(attendanceRepository).save(saved.capture());
        SessionAttendanceEntity row = saved.getValue();
        assertThat(row.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(row.getTenantId()).isEqualTo(TENANT);
        assertThat(row.getSubjectType()).isEqualTo("PROVIDER"); // from the matched enrolment
        assertThat(row.getSubjectId()).isEqualTo("provider-9");
        assertThat(row.getEnrolmentId()).isEqualTo(enrolment.getId());
        assertThat(row.getStatus()).isEqualTo("PRESENT");
        assertThat(row.getCheckInMethod()).isEqualTo(LiveAttendanceConsumer.CHECK_IN_METHOD_IMPILO_LIVE);
        // totalWatchMinutes wins over liveWatchMinutes
        assertThat(row.getWatchMinutes()).isEqualTo(50);
    }

    @Test
    void attendanceUpdated_redeliveryOverwritesAbsoluteMinutes_idempotent() {
        ScheduledLearningSessionEntity session = linkedSession();
        EnrolmentEntity enrolment = enrolment("provider-9", "IN_PROGRESS");
        SessionAttendanceEntity existing = new SessionAttendanceEntity();
        existing.setId(UUID.randomUUID());
        existing.setSessionId(SESSION_ID);
        existing.setSubjectType("PROVIDER");
        existing.setSubjectId("provider-9");
        existing.setEnrolmentId(enrolment.getId());
        existing.setWatchMinutes(50);
        when(sessionRepository.findFirstByLiveEventIdOrderByCreatedAtDesc(LIVE_EVENT_ID))
                .thenReturn(Optional.of(session));
        when(enrolmentRepository.findByTenantIdAndCourseId(TENANT, COURSE_ID))
                .thenReturn(List.of(enrolment));
        when(attendanceRepository.findBySessionIdAndSubjectTypeAndSubjectId(SESSION_ID, "PROVIDER", "provider-9"))
                .thenReturn(Optional.of(existing));
        when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Same event redelivered twice — minutes stay absolute, never accumulate.
        consumer.onAttendanceUpdated(attendancePayload("provider-9", 42, 50));
        consumer.onAttendanceUpdated(attendancePayload("provider-9", 42, 50));

        assertThat(existing.getWatchMinutes()).isEqualTo(50);
        verify(attendanceRepository, never()).findBySessionIdAndSubjectTypeAndSubjectId(
                SESSION_ID, LiveAttendanceConsumer.FALLBACK_SUBJECT_TYPE, "provider-9");
    }

    @Test
    void attendanceUpdated_withoutEnrolmentFallsBackToParticipantSubjectType() {
        ScheduledLearningSessionEntity session = linkedSession();
        when(sessionRepository.findFirstByLiveEventIdOrderByCreatedAtDesc(LIVE_EVENT_ID))
                .thenReturn(Optional.of(session));
        when(enrolmentRepository.findByTenantIdAndCourseId(TENANT, COURSE_ID)).thenReturn(List.of());
        when(attendanceRepository.findBySessionIdAndSubjectTypeAndSubjectId(
                SESSION_ID, LiveAttendanceConsumer.FALLBACK_SUBJECT_TYPE, "unknown-participant"))
                .thenReturn(Optional.empty());
        when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onAttendanceUpdated(attendancePayload("unknown-participant", 30, 30));

        ArgumentCaptor<SessionAttendanceEntity> saved = ArgumentCaptor.forClass(SessionAttendanceEntity.class);
        verify(attendanceRepository).save(saved.capture());
        assertThat(saved.getValue().getSubjectType())
                .isEqualTo(LiveAttendanceConsumer.FALLBACK_SUBJECT_TYPE);
        assertThat(saved.getValue().getEnrolmentId()).isNull();
    }

    @Test
    void attendanceUpdated_unlinkedLiveEventIsIgnored() {
        when(sessionRepository.findFirstByLiveEventIdOrderByCreatedAtDesc(LIVE_EVENT_ID))
                .thenReturn(Optional.empty());

        consumer.onAttendanceUpdated(attendancePayload("provider-9", 42, 50));

        verifyNoInteractions(attendanceRepository, enrolmentRepository, liveCompletionService);
    }

    @Test
    void attendanceUpdated_toleratesFlatUnwrappedPayload() {
        ScheduledLearningSessionEntity session = linkedSession();
        when(sessionRepository.findFirstByLiveEventIdOrderByCreatedAtDesc(LIVE_EVENT_ID))
                .thenReturn(Optional.of(session));
        when(enrolmentRepository.findByTenantIdAndCourseId(TENANT, COURSE_ID)).thenReturn(List.of());
        when(attendanceRepository.findBySessionIdAndSubjectTypeAndSubjectId(any(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String flat = "{\"eventId\":\"" + LIVE_EVENT_ID + "\",\"participantId\":\"p-1\",\"totalWatchMinutes\":15}";
        consumer.onAttendanceUpdated(flat);

        verify(attendanceRepository).save(any());
    }

    // ── event.ended ───────────────────────────────────────────────────────────

    @Test
    void eventEnded_runsCompletionPassOverLinkedSession() {
        ScheduledLearningSessionEntity session = linkedSession();
        when(sessionRepository.findFirstByLiveEventIdOrderByCreatedAtDesc(LIVE_EVENT_ID))
                .thenReturn(Optional.of(session));
        when(liveCompletionService.reconcileSessionCompletion(TENANT, session))
                .thenReturn(Map.of("completed", 1));

        consumer.onEventEnded(endedPayload());

        verify(liveCompletionService).reconcileSessionCompletion(TENANT, session);
    }

    @Test
    void eventEnded_unlinkedEventIsIgnored() {
        when(sessionRepository.findFirstByLiveEventIdOrderByCreatedAtDesc(LIVE_EVENT_ID))
                .thenReturn(Optional.empty());

        consumer.onEventEnded(endedPayload());

        verifyNoInteractions(liveCompletionService);
    }

    // ── poison messages ───────────────────────────────────────────────────────

    @Test
    void poisonMessagesAreSwallowedNotThrown() {
        assertThatCode(() -> {
            consumer.onAttendanceUpdated("not-json{{{");
            consumer.onAttendanceUpdated("{}");
            consumer.onEventEnded("not-json{{{");
            consumer.onEventEnded("{}");
        }).doesNotThrowAnyException();
        verifyNoInteractions(attendanceRepository, liveCompletionService);
    }

    @Test
    void downstreamFailureIsSwallowed() {
        ScheduledLearningSessionEntity session = linkedSession();
        when(sessionRepository.findFirstByLiveEventIdOrderByCreatedAtDesc(LIVE_EVENT_ID))
                .thenReturn(Optional.of(session));
        when(liveCompletionService.reconcileSessionCompletion(TENANT, session))
                .thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> consumer.onEventEnded(endedPayload())).doesNotThrowAnyException();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ScheduledLearningSessionEntity linkedSession() {
        ScheduledLearningSessionEntity s = new ScheduledLearningSessionEntity();
        s.setId(SESSION_ID);
        s.setTenantId(TENANT);
        s.setCourseId(COURSE_ID);
        s.setSessionMode("LIVE");
        s.setLiveEventId(LIVE_EVENT_ID);
        s.setJoinPath("/live/event/" + LIVE_EVENT_ID);
        return s;
    }

    private static EnrolmentEntity enrolment(String subjectId, String status) {
        EnrolmentEntity e = new EnrolmentEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setCourseId(COURSE_ID);
        e.setSubjectType("PROVIDER");
        e.setSubjectId(subjectId);
        e.setStatus(status);
        return e;
    }

    /** Mirrors LiveEventEmitter's envelope: {eventType, aggregateType, …, payload:{…}}. */
    private static String attendancePayload(String participantId, int liveMinutes, int totalMinutes) {
        return "{"
                + "\"eventType\":\"impilo.live.attendance.updated.v1\","
                + "\"aggregateType\":\"LIVE_ATTENDANCE\","
                + "\"aggregateId\":\"" + UUID.randomUUID() + "\","
                + "\"occurredAt\":\"2026-07-05T10:00:00Z\","
                + "\"payload\":{"
                + "\"attendanceId\":\"" + UUID.randomUUID() + "\","
                + "\"eventId\":\"" + LIVE_EVENT_ID + "\","
                + "\"participantId\":\"" + participantId + "\","
                + "\"participantType\":\"PARTICIPANT\","
                + "\"liveWatchMinutes\":" + liveMinutes + ","
                + "\"replayWatchMinutes\":0,"
                + "\"totalWatchMinutes\":" + totalMinutes + ","
                + "\"completionStatus\":\"IN_PROGRESS\","
                + "\"eligibleForCpd\":false,"
                + "\"eligibleForCertificate\":false"
                + "}}";
    }

    private static String endedPayload() {
        return "{"
                + "\"eventType\":\"impilo.live.event.ended.v1\","
                + "\"aggregateType\":\"LIVE_EVENT\","
                + "\"aggregateId\":\"" + LIVE_EVENT_ID + "\","
                + "\"occurredAt\":\"2026-07-05T11:00:00Z\","
                + "\"payload\":{"
                + "\"eventId\":\"" + LIVE_EVENT_ID + "\","
                + "\"title\":\"CPD Webinar\","
                + "\"status\":\"ENDED\","
                + "\"contextType\":\"LEARNING\","
                + "\"facilityId\":null"
                + "}}";
    }
}
