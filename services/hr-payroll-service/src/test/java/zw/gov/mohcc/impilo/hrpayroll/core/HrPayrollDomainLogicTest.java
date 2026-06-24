package zw.gov.mohcc.impilo.hrpayroll.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.*;
import zw.gov.mohcc.impilo.hrpayroll.persistence.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrPayrollDomainLogicTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000099");

    // ── G022: leave request reserves balance ──

    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private LeaveBalanceRepository leaveBalanceRepository;
    @Mock private HrOutboxWriter outbox;

    @Test
    void leaveRequestDecrementsBalance() throws Exception {
        LeaveService svc = new LeaveService(leaveRequestRepository, leaveBalanceRepository, outbox);
        UUID emp = UUID.randomUUID();
        UUID type = UUID.randomUUID();

        LeaveBalanceEntity bal = new LeaveBalanceEntity();
        bal.setEmployeeId(emp);
        bal.setLeaveTypeId(type);
        bal.setFiscalYear(2026);
        bal.setEntitlement(10);
        bal.setUsed(0);
        bal.setAvailable(10);
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndFiscalYear(emp, type, 2026))
                .thenReturn(Optional.of(bal));

        LeaveRequestEntity r = new LeaveRequestEntity();
        r.setRequestId(UUID.randomUUID());
        r.setEmployeeId(emp);
        r.setLeaveTypeId(type);
        r.setStartDate(LocalDate.of(2026, 3, 2));
        r.setEndDate(LocalDate.of(2026, 3, 4));
        r.setDays(3);

        LeaveRequestEntity saved = svc.requestLeave(TENANT, r);

        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(bal.getUsed()).isEqualTo(3);
        assertThat(bal.getAvailable()).isEqualTo(7);
        verify(leaveBalanceRepository).save(bal);
        verify(leaveRequestRepository).save(r);
    }

    @Test
    void leaveRequestRejectedWhenInsufficientBalance() {
        LeaveService svc = new LeaveService(leaveRequestRepository, leaveBalanceRepository, outbox);
        UUID emp = UUID.randomUUID();
        UUID type = UUID.randomUUID();

        LeaveBalanceEntity bal = new LeaveBalanceEntity();
        bal.setAvailable(2);
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndFiscalYear(emp, type, 2026))
                .thenReturn(Optional.of(bal));

        LeaveRequestEntity r = new LeaveRequestEntity();
        r.setEmployeeId(emp);
        r.setLeaveTypeId(type);
        r.setStartDate(LocalDate.of(2026, 1, 1));
        r.setDays(5);

        assertThatThrownBy(() -> svc.requestLeave(TENANT, r))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient");
        verify(leaveRequestRepository, never()).save(any());
    }

    // ── G042: attendance computes hours/overtime ──

    @Mock private AttendanceRepository attendanceRepository;

    @Test
    void attendanceComputesHoursAndOvertime() {
        AttendanceService svc = new AttendanceService(attendanceRepository);
        when(attendanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AttendanceRecordEntity a = new AttendanceRecordEntity();
        a.setEmployeeId(UUID.randomUUID());
        a.setShiftDate(LocalDate.of(2026, 3, 2));
        a.setClockIn(OffsetDateTime.of(2026, 3, 2, 8, 0, 0, 0, ZoneOffset.UTC));
        a.setClockOut(OffsetDateTime.of(2026, 3, 2, 18, 0, 0, 0, ZoneOffset.UTC));

        AttendanceRecordEntity out = svc.record(a);

        assertThat(out.getHoursWorked()).isEqualByComparingTo("10.00");
        assertThat(out.getOvertimeHours()).isEqualByComparingTo("2.00");
        assertThat(out.getStatus()).isEqualTo("PRESENT");
    }

    @Test
    void attendanceWithoutClockOutIsOpen() {
        AttendanceService svc = new AttendanceService(attendanceRepository);
        when(attendanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AttendanceRecordEntity a = new AttendanceRecordEntity();
        a.setClockIn(OffsetDateTime.of(2026, 3, 2, 8, 0, 0, 0, ZoneOffset.UTC));

        AttendanceRecordEntity out = svc.record(a);

        assertThat(out.getHoursWorked()).isNull();
        assertThat(out.getStatus()).isEqualTo("OPEN");
    }

    // ── G043: earnings = basic + allowances + overtime ──

    @Mock private PayrollRunRepository payrollRunRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private DeductionTypeRepository deductionTypeRepository;
    @Mock private VashandiAttendanceClient vashandiAttendanceClient;

    @Test
    void payrollGrossIncludesAllowancesAndOvertime() throws Exception {
        PayrollService svc = new PayrollService(payrollRunRepository, payslipRepository,
                employeeRepository, contractRepository, deductionTypeRepository,
                vashandiAttendanceClient, new ObjectMapper(), outbox);

        UUID runId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();

        PayrollRunEntity run = new PayrollRunEntity();
        run.setRunId(runId);
        run.setTenantId(TENANT);
        run.setPeriodYear(2026);
        run.setPeriodMonth(3);
        run.setStatus("DRAFT");
        when(payrollRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(payslipRepository.findByRunId(runId)).thenReturn(List.of());

        EmployeeEntity emp = new EmployeeEntity();
        emp.setEmployeeId(empId);
        emp.setTenantId(TENANT);
        emp.setEmploymentStatus("ACTIVE");
        emp.setProviderId("PRV-7");
        when(employeeRepository.findByTenantIdOrderByStaffNumberAsc(TENANT)).thenReturn(List.of(emp));

        ContractEntity contract = new ContractEntity();
        contract.setEmployeeId(empId);
        contract.setBasicSalary(new BigDecimal("1760"));
        contract.setAllowancesJson("{\"housing\":200}");
        when(contractRepository.findByEmployeeIdOrderByStartDateDesc(empId)).thenReturn(List.of(contract));

        when(deductionTypeRepository.findByTenantIdOrderByNameAsc(TENANT)).thenReturn(List.of());

        // 10 overtime hours from Vashandi → hourlyRate 1760/176 = 10 → OT pay 10*10*1.5 = 150
        when(vashandiAttendanceClient.overtimeHours(TENANT, "PRV-7", 2026, 3))
                .thenReturn(Optional.of(new BigDecimal("10")));

        // Assign id on persist so the outbox can reference it (PrePersist not triggered under mock).
        when(payslipRepository.save(any())).thenAnswer(i -> {
            PayslipEntity p = i.getArgument(0);
            if (p.getPayslipId() == null) {
                p.setPayslipId(UUID.randomUUID());
            }
            return p;
        });
        lenient().when(payrollRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        svc.calculate(TENANT, runId);

        ArgumentCaptor<PayslipEntity> captor = ArgumentCaptor.forClass(PayslipEntity.class);
        verify(payslipRepository).save(captor.capture());
        PayslipEntity slip = captor.getValue();

        assertThat(slip.getBasicSalary()).isEqualByComparingTo("1760");
        // gross = 1760 basic + 200 allowances + 150 overtime
        assertThat(slip.getGrossPay()).isEqualByComparingTo("2110.00");
        assertThat(slip.getNetPay()).isEqualByComparingTo("2110.00");
        assertThat(slip.getAllowancesJson()).contains("\"overtimePay\":150");
        assertThat(run.getTotalGross()).isEqualByComparingTo("2110.00");
    }
}
