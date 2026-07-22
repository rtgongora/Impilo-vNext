package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.core.telemedicine.ReferralTransitionGuardProperties;
import zw.gov.mohcc.impilo.pct.domain.ReferralStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReferralRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the lifecycle timer transitions over-threshold pre-accept referrals to
 * EXPIRED (and scheduled no-shows to ABANDONED) while leaving under-threshold
 * referrals untouched. Uses a real {@link ReferralStateMachine} (default SHADOW
 * mode) so the actual transition matrix and TrustContext-free apply path run.
 */
@ExtendWith(MockitoExtension.class)
class ReferralLifecycleTimerJobTest {

    @Mock private ReferralRepository referralRepository;
    @Mock private VirtualPoolQueueService virtualPoolQueueService;
    @Mock private EventOutboxRepository outboxRepository;

    private ReferralLifecycleTimerJob job;

    @BeforeEach
    void setUp() {
        // Real state machine: real matrix, mocked ledger recorder, default SHADOW mode.
        ReferralStateMachine stateMachine = new ReferralStateMachine(
                mock(ReferralTransitionRecorder.class), new ReferralTransitionGuardProperties());
        job = new ReferralLifecycleTimerJob(referralRepository, stateMachine,
                virtualPoolQueueService, outboxRepository, new ObjectMapper(), null);
        job.setThresholds(1440, 120); // 24h expiry, 2h no-show
    }

    private ReferralEntity referral(String status) {
        ReferralEntity e = new ReferralEntity();
        e.setReferralId(UUID.randomUUID());
        e.setTenantId(UUID.randomUUID());
        e.setPatientCpid("CPID-1");
        e.setStatus(status);
        return e;
    }

    @Test
    void overThresholdSubmittedReferralIsExpired() {
        ReferralEntity r = referral(ReferralStatus.SUBMITTED.name());
        r.setSubmittedAt(OffsetDateTime.now().minusDays(2)); // older than 24h
        when(referralRepository.findTop100ByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(r));
        when(referralRepository.findByReferralId(r.getReferralId())).thenReturn(Optional.of(r));
        when(referralRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int expired = job.expireStaleReferrals();

        assertThat(expired).isEqualTo(1);
        assertThat(r.getStatus()).isEqualTo(ReferralStatus.EXPIRED.name());
        // Pool worklist item retired so pool + case state agree.
        verify(virtualPoolQueueService).completeForReferral(eq(r), eq(ReferralLifecycleTimerJob.SYSTEM_ACTOR));

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        // TM-B20: lifecycle events are versioned .v1 at the outbox choke point.
        assertThat(outbox.getValue().getEventType()).isEqualTo("telemedicine.session.expired.v1");
        assertThat(outbox.getValue().getPayload()).contains("EXPIRED").contains(r.getReferralId().toString());
    }

    @Test
    void overThresholdRoutedReferralIsExpired() {
        ReferralEntity r = referral(ReferralStatus.ROUTED.name());
        r.setRoutedAt(OffsetDateTime.now().minusDays(3)); // older than 24h
        when(referralRepository.findTop100ByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(r));
        when(referralRepository.findByReferralId(r.getReferralId())).thenReturn(Optional.of(r));
        when(referralRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(job.expireStaleReferrals()).isEqualTo(1);
        assertThat(r.getStatus()).isEqualTo(ReferralStatus.EXPIRED.name());
    }

    @Test
    void underThresholdSubmittedReferralIsUntouched() {
        ReferralEntity r = referral(ReferralStatus.SUBMITTED.name());
        r.setSubmittedAt(OffsetDateTime.now().minusMinutes(5)); // well within 24h
        when(referralRepository.findTop100ByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(r));

        int expired = job.expireStaleReferrals();

        assertThat(expired).isZero();
        assertThat(r.getStatus()).isEqualTo(ReferralStatus.SUBMITTED.name());
        verify(referralRepository, never()).findByReferralId(any());
        verify(referralRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void overThresholdScheduledNoShowIsAbandoned() {
        ReferralEntity r = referral(ReferralStatus.SCHEDULED.name());
        r.setScheduledAt(OffsetDateTime.now().minusHours(3)); // past 2h no-show cutoff
        when(referralRepository.findTop100ByStatusAndScheduledAtBeforeOrderByScheduledAtAsc(
                eq(ReferralStatus.SCHEDULED.name()), any())).thenReturn(List.of(r));
        when(referralRepository.findByReferralId(r.getReferralId())).thenReturn(Optional.of(r));
        when(referralRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int abandoned = job.abandonNoShowReferrals();

        assertThat(abandoned).isEqualTo(1);
        assertThat(r.getStatus()).isEqualTo(ReferralStatus.ABANDONED.name());

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        // TM-B20: lifecycle events are versioned .v1 at the outbox choke point.
        assertThat(outbox.getValue().getEventType()).isEqualTo("telemedicine.session.abandoned.v1");
    }
}
