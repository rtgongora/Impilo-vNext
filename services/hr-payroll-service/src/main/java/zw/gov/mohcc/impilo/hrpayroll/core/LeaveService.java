package zw.gov.mohcc.impilo.hrpayroll.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.LeaveBalanceEntity;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.LeaveRequestEntity;
import zw.gov.mohcc.impilo.hrpayroll.persistence.repository.LeaveBalanceRepository;
import zw.gov.mohcc.impilo.hrpayroll.persistence.repository.LeaveRequestRepository;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Leave-request lifecycle. A new request **reserves** balance against the matching
 * {@link LeaveBalanceEntity} (used += days, available -= days) so the entitlement
 * reflects in-flight requests and cannot be over-booked. Previously the request was
 * persisted with no effect on the balance (G022).
 */
@Service
public class LeaveService {

    private static final Logger log = LoggerFactory.getLogger(LeaveService.class);

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final HrOutboxWriter outbox;

    public LeaveService(LeaveRequestRepository leaveRequestRepository,
                        LeaveBalanceRepository leaveBalanceRepository,
                        HrOutboxWriter outbox) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.outbox = outbox;
    }

    @Transactional
    public LeaveRequestEntity requestLeave(UUID tenantId, LeaveRequestEntity r) throws Exception {
        if (r.getEmployeeId() == null || r.getLeaveTypeId() == null || r.getStartDate() == null) {
            throw new IllegalArgumentException("employeeId, leaveTypeId and startDate are required");
        }

        int days = r.getDays();
        if (days <= 0 && r.getEndDate() != null) {
            // Inclusive day count when the caller did not pre-compute it.
            days = (int) (ChronoUnit.DAYS.between(r.getStartDate(), r.getEndDate()) + 1);
            r.setDays(days);
        }
        if (days <= 0) {
            throw new IllegalArgumentException("Leave duration must be at least one day");
        }

        int fiscalYear = r.getStartDate().getYear();
        LeaveBalanceEntity balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndFiscalYear(r.getEmployeeId(), r.getLeaveTypeId(), fiscalYear)
                .orElseThrow(() -> new IllegalStateException(
                        "No leave balance for employee " + r.getEmployeeId()
                                + " leaveType " + r.getLeaveTypeId() + " fiscalYear " + fiscalYear));

        if (balance.getAvailable() < days) {
            throw new IllegalArgumentException(
                    "Insufficient leave balance: available=" + balance.getAvailable() + ", requested=" + days);
        }

        balance.setUsed(balance.getUsed() + days);
        balance.setAvailable(balance.getAvailable() - days);
        leaveBalanceRepository.save(balance);

        r.setStatus("PENDING");
        leaveRequestRepository.save(r);

        outbox.publish(tenantId, "LEAVE_REQUEST", r.getRequestId().toString(), "leave_request", "reserved",
                "hr:leave_request:" + r.getRequestId() + ":reserved",
                Map.of("tenantId", tenantId.toString(),
                        "requestId", r.getRequestId().toString(),
                        "employeeId", r.getEmployeeId().toString(),
                        "leaveTypeId", r.getLeaveTypeId().toString(),
                        "days", days,
                        "fiscalYear", fiscalYear,
                        "balanceAvailable", balance.getAvailable()));

        log.info("Reserved leave [request={}, employee={}, days={}, remaining={}]",
                r.getRequestId(), r.getEmployeeId(), days, balance.getAvailable());
        return r;
    }
}
