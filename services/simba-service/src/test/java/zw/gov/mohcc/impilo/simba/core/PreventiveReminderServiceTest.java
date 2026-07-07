package zw.gov.mohcc.impilo.simba.core;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.simba.persistence.entity.PreventiveReminderEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.PreventiveReminderRepository;

import java.time.OffsetDateTime;
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

/** Unit test for reminder firing: state transition, Khuluma emit, timeline, integration, cadence reschedule. */
class PreventiveReminderServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String CPID = "unit-reminder-001";

    private PreventiveReminderEntity reminder(int cadenceMonths) {
        PreventiveReminderEntity r = new PreventiveReminderEntity();
        r.setReminderId(UUID.randomUUID());
        r.setTenantId(TENANT);
        r.setPersonCpid(CPID);
        r.setReminderType("SCREENING");
        r.setTitle("BP check");
        r.setDetail("Time for your blood-pressure check");
        r.setDueAt(OffsetDateTime.now().minusMinutes(5));
        if (cadenceMonths > 0) {
            r.setCadenceMonths(cadenceMonths);
        }
        r.setChannel("PUSH");
        return r;
    }

    @Test
    void fireDue_firesEmitsTimelineAndIntegration() {
        PreventiveReminderRepository repo = mock(PreventiveReminderRepository.class);
        SimbaEventEmitter events = mock(SimbaEventEmitter.class);
        TimelineService timeline = mock(TimelineService.class);
        IntegrationEventService integrations = mock(IntegrationEventService.class);

        PreventiveReminderEntity r = reminder(0);
        when(repo.findDue(any(OffsetDateTime.class), any(Pageable.class))).thenReturn(List.of(r));

        PreventiveReminderService svc = new PreventiveReminderService(repo, events, timeline, integrations);
        int fired = svc.fireDue(10);

        assertThat(fired).isEqualTo(1);
        assertThat(r.getStatus()).isEqualTo("FIRED");
        assertThat(r.getLastFiredAt()).isNotNull();

        // Khuluma seam event emitted with the reminder-due topic.
        verify(events).emit(eq(TENANT), eq("WELLNESS_REMINDER"), anyString(),
                eq("impilo.simba.wellness.reminder.due.v1"), eq(CPID), anyString(), any());
        // Person-facing timeline written.
        verify(timeline).record(eq(TENANT), eq(CPID), eq("REMINDER"), anyString(), any(),
                anyString(), anyString(), anyString(), any());
        // Honest handoff ledger row (PENDING, KHULUMA).
        verify(integrations).record(eq(TENANT), eq(CPID), eq("KHULUMA"),
                eq("wellness.reminder.deliver"), eq("PENDING"), any(Map.class), anyString());
    }

    @Test
    void fireDue_reschedulesNextOccurrenceWhenCadenceSet() {
        PreventiveReminderRepository repo = mock(PreventiveReminderRepository.class);
        SimbaEventEmitter events = mock(SimbaEventEmitter.class);
        TimelineService timeline = mock(TimelineService.class);
        IntegrationEventService integrations = mock(IntegrationEventService.class);

        PreventiveReminderEntity r = reminder(12);
        OffsetDateTime originalDue = r.getDueAt();
        when(repo.findDue(any(OffsetDateTime.class), any(Pageable.class))).thenReturn(List.of(r));

        PreventiveReminderService svc = new PreventiveReminderService(repo, events, timeline, integrations);
        svc.fireDue(10);

        ArgumentCaptor<PreventiveReminderEntity> saved = ArgumentCaptor.forClass(PreventiveReminderEntity.class);
        // At least two saves: the next occurrence + the fired reminder.
        verify(repo, times(2)).save(saved.capture());
        PreventiveReminderEntity next = saved.getAllValues().stream()
                .filter(s -> "SCHEDULED".equals(s.getStatus()))
                .findFirst().orElseThrow();
        assertThat(next.getDueAt()).isEqualTo(originalDue.plusMonths(12));
        assertThat(next.getCadenceMonths()).isEqualTo(12);
    }

    @Test
    void fireDue_emptyDueSet_returnsZero() {
        PreventiveReminderRepository repo = mock(PreventiveReminderRepository.class);
        when(repo.findDue(any(OffsetDateTime.class), any(Pageable.class))).thenReturn(List.of());
        PreventiveReminderService svc = new PreventiveReminderService(repo,
                mock(SimbaEventEmitter.class), mock(TimelineService.class), mock(IntegrationEventService.class));
        assertThat(svc.fireDue(10)).isZero();
    }
}
