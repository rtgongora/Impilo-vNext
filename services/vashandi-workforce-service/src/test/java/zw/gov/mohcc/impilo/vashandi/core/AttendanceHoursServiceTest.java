package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AttendanceEventEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.AttendanceEventRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceHoursServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000abc");

    @Mock AttendanceEventRepository attendanceRepository;
    @Mock WorkforceProfileRepository profileRepository;

    private AttendanceEventEntity event(UUID profileId, String type, OffsetDateTime when) {
        AttendanceEventEntity e = new AttendanceEventEntity();
        e.setTenantId(TENANT);
        e.setWorkforceProfileId(profileId);
        e.setEventType(type);
        e.setEventTime(when);
        return e;
    }

    private OffsetDateTime at(int day, int hour) {
        return OffsetDateTime.of(2026, 3, day, hour, 0, 0, 0, ZoneOffset.UTC);
    }

    @Test
    void pairsCheckInOutAndComputesOvertime() {
        AttendanceHoursService svc = new AttendanceHoursService(attendanceRepository, profileRepository);
        UUID profile = UUID.randomUUID();
        // Day 2: 08:00–18:00 = 10h (2 OT). Day 3: 08:00–16:00 = 8h (0 OT). Total 18h, 2 OT, 2 pairs.
        when(attendanceRepository.findByTenantIdAndWorkforceProfileIdAndEventTimeBetweenOrderByEventTimeAsc(
                eq(TENANT), eq(profile), any(), any()))
                .thenReturn(List.of(
                        event(profile, "check_in", at(2, 8)),
                        event(profile, "check_out", at(2, 18)),
                        event(profile, "check_in", at(3, 8)),
                        event(profile, "supervisor_confirmed_check_out", at(3, 16))));

        var summary = svc.summarize(TENANT, profile, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(summary.totalHours()).isEqualByComparingTo("18.00");
        assertThat(summary.overtimeHours()).isEqualByComparingTo("2.00");
        assertThat(summary.pairedShifts()).isEqualTo(2);
    }

    @Test
    void unpairedCheckInContributesNothing() {
        AttendanceHoursService svc = new AttendanceHoursService(attendanceRepository, profileRepository);
        UUID profile = UUID.randomUUID();
        when(attendanceRepository.findByTenantIdAndWorkforceProfileIdAndEventTimeBetweenOrderByEventTimeAsc(
                any(), any(), any(), any()))
                .thenReturn(List.of(event(profile, "check_in", at(2, 8)))); // no check-out

        var summary = svc.summarize(TENANT, profile, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(summary.totalHours()).isEqualByComparingTo("0");
        assertThat(summary.overtimeHours()).isEqualByComparingTo("0");
        assertThat(summary.pairedShifts()).isZero();
    }

    @Test
    void resolvesByProviderWorkerIdViaIdentityBridge() {
        AttendanceHoursService svc = new AttendanceHoursService(attendanceRepository, profileRepository);
        UUID profileId = UUID.randomUUID();
        WorkforceProfileEntity profile = new WorkforceProfileEntity();
        profile.setId(profileId);
        profile.setTenantId(TENANT);
        when(profileRepository.findByTenantIdAndProviderWorkerId(TENANT, "PRV-9"))
                .thenReturn(Optional.of(profile));
        when(attendanceRepository.findByTenantIdAndWorkforceProfileIdAndEventTimeBetweenOrderByEventTimeAsc(
                eq(TENANT), eq(profileId), any(), any()))
                .thenReturn(List.of(event(profileId, "check_in", at(2, 8)), event(profileId, "check_out", at(2, 17))));

        var summary = svc.summarizeMonthByProviderWorkerId(TENANT, "PRV-9", 2026, 3);

        assertThat(summary).isPresent();
        assertThat(summary.get().totalHours()).isEqualByComparingTo("9.00");
        assertThat(summary.get().overtimeHours()).isEqualByComparingTo("1.00");
    }

    @Test
    void unmappedWorkerReturnsEmpty() {
        AttendanceHoursService svc = new AttendanceHoursService(attendanceRepository, profileRepository);
        when(profileRepository.findByTenantIdAndProviderWorkerId(TENANT, "missing"))
                .thenReturn(Optional.empty());

        assertThat(svc.summarizeMonthByProviderWorkerId(TENANT, "missing", 2026, 3)).isEmpty();
    }
}
