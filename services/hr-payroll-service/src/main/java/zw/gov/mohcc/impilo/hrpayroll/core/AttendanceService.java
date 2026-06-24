package zw.gov.mohcc.impilo.hrpayroll.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.AttendanceRecordEntity;
import zw.gov.mohcc.impilo.hrpayroll.persistence.repository.AttendanceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * Attendance capture. When both clock-in and clock-out are present, the worked
 * hours and overtime are **computed** from the timestamps (hours beyond the
 * standard shift count as overtime) rather than trusted from the request body or
 * left null (G042).
 */
@Service
public class AttendanceService {

    /** Standard shift length; hours beyond this in a single record are overtime. */
    static final BigDecimal STANDARD_SHIFT_HOURS = new BigDecimal("8");

    private final AttendanceRepository attendanceRepository;

    public AttendanceService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public AttendanceRecordEntity record(AttendanceRecordEntity a) {
        if (a.getClockIn() != null && a.getClockOut() != null) {
            long minutes = Duration.between(a.getClockIn(), a.getClockOut()).toMinutes();
            BigDecimal worked = BigDecimal.valueOf(Math.max(minutes, 0))
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            a.setHoursWorked(worked);
            BigDecimal overtime = worked.subtract(STANDARD_SHIFT_HOURS);
            a.setOvertimeHours(overtime.signum() > 0 ? overtime : BigDecimal.ZERO);
            if (a.getStatus() == null || a.getStatus().isBlank()) {
                a.setStatus("PRESENT");
            }
        } else if (a.getStatus() == null || a.getStatus().isBlank()) {
            a.setStatus("OPEN");
        }
        return attendanceRepository.save(a);
    }
}
