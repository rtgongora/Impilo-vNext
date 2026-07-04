package zw.gov.mohcc.impilo.khuluma.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.khuluma.core.ActorContext;
import zw.gov.mohcc.impilo.khuluma.core.CallService;
import zw.gov.mohcc.impilo.khuluma.domain.CallEntity;
import zw.gov.mohcc.impilo.khuluma.domain.CallEventEntity;
import zw.gov.mohcc.impilo.khuluma.domain.CallParticipantEntity;
import zw.gov.mohcc.impilo.khuluma.repository.CallEventRepository;
import zw.gov.mohcc.impilo.khuluma.repository.CallParticipantRepository;
import zw.gov.mohcc.impilo.khuluma.repository.CallRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RtcCallEventsConsumerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CALL_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String RTC_SESSION_ID = CALL_ID.toString();

    @Mock private CallRepository calls;
    @Mock private CallParticipantRepository callParticipants;
    @Mock private CallEventRepository callEvents;
    @Mock private CallService callService;

    private RtcCallEventsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RtcCallEventsConsumer(new ObjectMapper(),
                calls, callParticipants, callEvents, callService);
        lenient().when(callParticipants.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(callEvents.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── participant.joined ───────────────────────────────────────────────────

    @Test
    void participantJoined_marksParticipantJoined_andAppendsAuditRow() {
        stubCall(call("ACTIVE"));
        CallParticipantEntity participant = participant("RINGING", null);
        when(callParticipants.findByCallIdAndActorId(CALL_ID, "provider-b"))
                .thenReturn(Optional.of(participant));

        consumer.onParticipantJoined(joinedPayload("KHULUMA", "provider-b"));

        assertThat(participant.getState()).isEqualTo("JOINED");
        assertThat(participant.getJoinedAt()).isNotNull();
        verify(callParticipants).save(participant);
        ArgumentCaptor<CallEventEntity> audit = ArgumentCaptor.forClass(CallEventEntity.class);
        verify(callEvents).save(audit.capture());
        assertThat(audit.getValue().getEventType())
                .isEqualTo(RtcCallEventsConsumer.EVENT_RTC_PARTICIPANT_JOINED);
        assertThat(audit.getValue().getCallId()).isEqualTo(CALL_ID);
        assertThat(audit.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(audit.getValue().getActorId()).isEqualTo("provider-b");
        assertThat(audit.getValue().getDetailJson()).contains("participantIdentity");
    }

    @Test
    void participantJoined_alreadyJoined_isIdempotentNoOp() {
        stubCall(call("ACTIVE"));
        when(callParticipants.findByCallIdAndActorId(CALL_ID, "provider-b"))
                .thenReturn(Optional.of(participant("JOINED", OffsetDateTime.now())));

        consumer.onParticipantJoined(joinedPayload("KHULUMA", "provider-b"));

        verify(callParticipants, never()).save(any());
        verify(callEvents, never()).save(any());
    }

    @Test
    void participantJoined_ignoresOtherOwningServicesSilently() {
        consumer.onParticipantJoined(joinedPayload("LIVE", "provider-b"));
        consumer.onParticipantJoined(joinedPayload("PCT", "provider-b"));

        verifyNoInteractions(calls, callParticipants, callEvents, callService);
    }

    @Test
    void participantJoined_unknownIdentity_stillAppendsAuditRow() {
        stubCall(call("ACTIVE"));
        when(callParticipants.findByCallIdAndActorId(CALL_ID, "observer-x"))
                .thenReturn(Optional.empty());

        consumer.onParticipantJoined(joinedPayload("KHULUMA", "observer-x"));

        verify(callParticipants, never()).save(any());
        verify(callEvents).save(any(CallEventEntity.class));
    }

    // ── participant.left ─────────────────────────────────────────────────────

    @Test
    void participantLeft_marksParticipantLeft_andAppendsAuditRow() {
        stubCall(call("ACTIVE"));
        CallParticipantEntity participant = participant("JOINED", OffsetDateTime.now().minusMinutes(10));
        when(callParticipants.findByCallIdAndActorId(CALL_ID, "provider-b"))
                .thenReturn(Optional.of(participant));

        consumer.onParticipantLeft(leftPayload("KHULUMA", "provider-b"));

        assertThat(participant.getState()).isEqualTo("LEFT");
        assertThat(participant.getLeftAt()).isNotNull();
        verify(callParticipants).save(participant);
        ArgumentCaptor<CallEventEntity> audit = ArgumentCaptor.forClass(CallEventEntity.class);
        verify(callEvents).save(audit.capture());
        assertThat(audit.getValue().getEventType())
                .isEqualTo(RtcCallEventsConsumer.EVENT_RTC_PARTICIPANT_LEFT);
    }

    @Test
    void participantLeft_alreadyLeft_isIdempotentNoOp() {
        stubCall(call("ACTIVE"));
        CallParticipantEntity participant = participant("LEFT", OffsetDateTime.now().minusMinutes(10));
        when(callParticipants.findByCallIdAndActorId(CALL_ID, "provider-b"))
                .thenReturn(Optional.of(participant));

        consumer.onParticipantLeft(leftPayload("KHULUMA", "provider-b"));

        verify(callParticipants, never()).save(any());
        verify(callEvents, never()).save(any());
    }

    // ── session.finished ─────────────────────────────────────────────────────

    @Test
    void sessionFinished_endsActiveCall_viaCallServiceWithSyntheticSystemActor() {
        stubCall(call("ACTIVE"));

        consumer.onSessionFinished(finishedPayload("KHULUMA", null));

        ArgumentCaptor<ActorContext> ctx = ArgumentCaptor.forClass(ActorContext.class);
        verify(callService).end(ctx.capture(),
                org.mockito.ArgumentMatchers.eq(CALL_ID),
                org.mockito.ArgumentMatchers.eq(RtcCallEventsConsumer.END_REASON_RTC_SESSION_FINISHED));
        assertThat(ctx.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(ctx.getValue().actorId()).isEqualTo(RtcCallEventsConsumer.RTC_ACTOR);
        assertThat(ctx.getValue().actorType()).isEqualTo("SYSTEM");
    }

    @Test
    void sessionFinished_nonActiveCall_isIdempotentNoOp() {
        stubCall(call("ENDED"));

        consumer.onSessionFinished(finishedPayload("KHULUMA", null));

        verify(callService, never()).end(any(), any(), anyString());
    }

    @Test
    void sessionFinished_resolvesByOwningRefWhenSessionLookupMisses() {
        when(calls.findFirstByRtcSessionIdOrderByInitiatedAtDesc(RTC_SESSION_ID))
                .thenReturn(Optional.empty());
        when(calls.findById(CALL_ID)).thenReturn(Optional.of(call("ACTIVE")));

        consumer.onSessionFinished(finishedPayload("KHULUMA", CALL_ID.toString()));

        verify(callService).end(any(), org.mockito.ArgumentMatchers.eq(CALL_ID), anyString());
    }

    @Test
    void sessionFinished_unknownSession_isSkipped() {
        when(calls.findFirstByRtcSessionIdOrderByInitiatedAtDesc(RTC_SESSION_ID))
                .thenReturn(Optional.empty());

        consumer.onSessionFinished(finishedPayload("KHULUMA", null));

        verifyNoInteractions(callService, callParticipants, callEvents);
    }

    // ── resilience ───────────────────────────────────────────────────────────

    @Test
    void poisonMessages_areSwallowedNotThrown() {
        assertThatCode(() -> {
            consumer.onSessionStarted("not json {{{");
            consumer.onSessionFinished("");
            consumer.onParticipantJoined("[]");
            consumer.onParticipantLeft("42");
        }).doesNotThrowAnyException();
    }

    @Test
    void downstreamFailure_isSwallowedNotThrown() {
        stubCall(call("ACTIVE"));
        when(callService.end(any(), any(), anyString()))
                .thenThrow(new IllegalStateException("downstream unavailable"));

        assertThatCode(() -> consumer.onSessionFinished(finishedPayload("KHULUMA", null)))
                .doesNotThrowAnyException();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void stubCall(CallEntity call) {
        when(calls.findFirstByRtcSessionIdOrderByInitiatedAtDesc(RTC_SESSION_ID))
                .thenReturn(Optional.of(call));
    }

    private static CallEntity call(String status) {
        CallEntity call = new CallEntity();
        call.setCallId(CALL_ID);
        call.setTenantId(TENANT_ID);
        call.setStatus(status);
        call.setRtcSessionId(RTC_SESSION_ID);
        call.setInitiatedBy("provider-a");
        return call;
    }

    private static CallParticipantEntity participant(String state, OffsetDateTime joinedAt) {
        CallParticipantEntity p = new CallParticipantEntity();
        p.setCallId(CALL_ID);
        p.setTenantId(TENANT_ID);
        p.setActorId("provider-b");
        p.setState(state);
        p.setJoinedAt(joinedAt);
        return p;
    }

    private static String joinedPayload(String owningService, String identity) {
        return """
                {"sessionId":"%s","sessionMode":"TELEMEDICINE","owningService":"%s",
                 "roomName":"impilo-call-room","eventId":"lk-evt-1","occurredAt":"2026-07-04T09:00:00Z",
                 "participantIdentity":"%s","unknownField":[1,2,3]}
                """.formatted(RTC_SESSION_ID, owningService, identity);
    }

    private static String finishedPayload(String owningService, String owningRef) {
        return """
                {"sessionId":"%s","sessionMode":"TELEMEDICINE","owningService":"%s",%s
                 "roomName":"impilo-call-room","eventId":"lk-evt-1","occurredAt":"2026-07-04T09:00:00Z",
                 "unknownField":[1,2,3]}
                """.formatted(RTC_SESSION_ID, owningService,
                owningRef == null ? "\"owningRef\":null," : "\"owningRef\":\"" + owningRef + "\",");
    }

    private static String leftPayload(String owningService, String identity) {
        return """
                {"sessionId":"%s","sessionMode":"TELEMEDICINE","owningService":"%s",
                 "roomName":"impilo-call-room","eventId":"lk-evt-2","occurredAt":"2026-07-04T09:30:00Z",
                 "participantIdentity":"%s","disconnectReason":"CLIENT_INITIATED"}
                """.formatted(RTC_SESSION_ID, owningService, identity);
    }
}
