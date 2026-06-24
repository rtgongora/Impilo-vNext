package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AttendanceEventEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.AttendanceEventRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Derives worked-hours and overtime from a worker's raw check-in/check-out attendance
 * events — the single source of truth for attendance. Consumed by payroll (via the
 * identity bridge) so hours are never separately re-entered (kills the double-entry).
 */
@Service
public class AttendanceHoursService {

    /** Standard shift length; worked hours beyond this within a single in→out pair are overtime. */
    static final BigDecimal STANDARD_SHIFT_HOURS = new BigDecimal("8");

    private final AttendanceEventRepository attendanceRepository;
    private final WorkforceProfileRepository profileRepository;

    public AttendanceHoursService(AttendanceEventRepository attendanceRepository,
                                  WorkforceProfileRepository profileRepository) {
        this.attendanceRepository = attendanceRepository;
        this.profileRepository = profileRepository;
    }

    /** Summary of derived attendance hours for a worker over a period. */
    public record HoursSummary(UUID workforceProfileId, LocalDate periodStart, LocalDate periodEnd,
                               BigDecimal totalHours, BigDecimal overtimeHours, int pairedShifts) {}

    private static boolean isCheckIn(String eventType) {
        return eventType != null && eventType.contains("check_in");
    }

    private static boolean isCheckOut(String eventType) {
        return eventType != null && eventType.contains("check_out");
    }

    /**
     * Pair check-in→check-out events chronologically and total the worked + overtime hours.
     * Unpaired check-ins (missing check-out) contribute nothing. Overnight pairs are handled
     * by duration. Overtime = max(0, worked − 8h) per pair (mirrors the prior payroll math).
     */
    @Transactional(readOnly = true)
    public HoursSummary summarize(UUID tenantId, UUID workforceProfileId, LocalDate start, LocalDate end) {
        OffsetDateTime from = start.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        List<AttendanceEventEntity> events = attendanceRepository
                .findByTenantIdAndWorkforceProfileIdAndEventTimeBetweenOrderByEventTimeAsc(
                        tenantId, workforceProfileId, from, to);

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal overtime = BigDecimal.ZERO;
        int pairs = 0;
        OffsetDateTime pendingIn = null;
        for (AttendanceEventEntity e : events) {
            if (isCheckIn(e.getEventType())) {
                pendingIn = e.getEventTime();
            } else if (isCheckOut(e.getEventType()) && pendingIn != null) {
                long minutes = Duration.between(pendingIn, e.getEventTime()).toMinutes();
                if (minutes > 0) {
                    BigDecimal worked = BigDecimal.valueOf(minutes)
                            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                    total = total.add(worked);
                    BigDecimal ot = worked.subtract(STANDARD_SHIFT_HOURS);
                    if (ot.signum() > 0) {
                        overtime = overtime.add(ot);
                    }
                    pairs++;
                }
                pendingIn = null;
            }
        }
        return new HoursSummary(workforceProfileId, start, end, total, overtime, pairs);
    }

    /** Calendar-month summary for the worker identified by provider-worker-id (the payroll bridge). */
    @Transactional(readOnly = true)
    public Optional<HoursSummary> summarizeMonthByProviderWorkerId(
            UUID tenantId, String providerWorkerId, int year, int month) {
        return profileRepository.findByTenantIdAndProviderWorkerId(tenantId, providerWorkerId)
                .map(p -> summarizeMonth(tenantId, p, year, month));
    }

    /** Calendar-month summary for the worker identified by health-id. */
    @Transactional(readOnly = true)
    public Optional<HoursSummary> summarizeMonthByHealthId(
            UUID tenantId, String healthId, int year, int month) {
        return profileRepository.findByTenantIdAndHealthId(tenantId, healthId)
                .map(p -> summarizeMonth(tenantId, p, year, month));
    }

    private HoursSummary summarizeMonth(UUID tenantId, WorkforceProfileEntity profile, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        return summarize(tenantId, profile.getId(), start, end);
    }
}
