package zw.gov.mohcc.impilo.scheduling;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.scheduling.core.SchedulingOutboxPublisher;
import zw.gov.mohcc.impilo.scheduling.core.SurgicalWaitlistService;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.SurgicalWaitlistEntryEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.SurgicalWaitlistRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SurgicalWaitlistServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    void placementIsIdempotentByReferral() {
        SurgicalWaitlistRepository repo = mock(SurgicalWaitlistRepository.class);
        SchedulingOutboxPublisher outbox = mock(SchedulingOutboxPublisher.class);
        SurgicalWaitlistService service = new SurgicalWaitlistService(repo, outbox);
        UUID referralId = UUID.randomUUID();

        // First placement: no existing active entry.
        when(repo.findByTenantIdAndReferralId(TENANT, referralId)).thenReturn(List.of());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        Map<String, Object> first = service.placeOnWaitlist(TENANT, referralId, "P-1", "ZIBO-1", "P2", null, null);
        assertThat(first.get("status")).isEqualTo("WAITING");
        verify(outbox).append(any(), anyString(), eq("scheduling.waitlist.placed"), any());

        // Redelivered event: an active entry now exists → no new row, no new event.
        SurgicalWaitlistEntryEntity existing =
                new SurgicalWaitlistEntryEntity(UUID.randomUUID(), TENANT, referralId, "P-1");
        when(repo.findByTenantIdAndReferralId(TENANT, referralId)).thenReturn(List.of(existing));
        Map<String, Object> second = service.placeOnWaitlist(TENANT, referralId, "P-1", "ZIBO-1", "P2", null, null);

        assertThat(second.get("id")).isEqualTo(existing.getId().toString());
        // Idempotent: still only the single placed event + single save from the first call.
        verify(outbox, times(1)).append(any(), anyString(), eq("scheduling.waitlist.placed"), any());
        verify(repo, times(1)).save(any());
    }

    @Test
    void escalationStateComputedAtReadNeverStale() {
        SurgicalWaitlistEntryEntity entry =
                new SurgicalWaitlistEntryEntity(UUID.randomUUID(), TENANT, UUID.randomUUID(), "P-9");
        entry.setTargetTimeframeDays(30);
        LocalDate today = LocalDate.of(2026, 1, 1);

        // Target far in the future → on target.
        entry.setTargetDate(today.plusDays(30));
        assertThat(entry.escalationState(today)).isEqualTo("ON_TARGET");

        // Target within the approaching window.
        entry.setTargetDate(today.plusDays(2));
        assertThat(entry.escalationState(today)).isEqualTo("APPROACHING");

        // Target in the past → breached.
        entry.setTargetDate(today.minusDays(1));
        assertThat(entry.escalationState(today)).isEqualTo("BREACHED");
    }

    @Test
    void defaultTimeframeTracksPriority() {
        SurgicalWaitlistRepository repo = mock(SurgicalWaitlistRepository.class);
        SchedulingOutboxPublisher outbox = mock(SchedulingOutboxPublisher.class);
        SurgicalWaitlistService service = new SurgicalWaitlistService(repo, outbox);
        when(repo.findByTenantIdAndReferralId(any(), any())).thenReturn(List.of());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> p1 = service.placeOnWaitlist(TENANT, UUID.randomUUID(), "P", "Z", "P1", null, null);
        assertThat(p1.get("targetTimeframeDays")).isEqualTo(7);
        Map<String, Object> p4 = service.placeOnWaitlist(TENANT, UUID.randomUUID(), "P", "Z", "P4", null, null);
        assertThat(p4.get("targetTimeframeDays")).isEqualTo(180);
    }
}
