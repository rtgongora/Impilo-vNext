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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Payroll earnings logic. Leave + attendance moved to Vashandi (the workforce SoR) and are
 * tested there ({@code LeaveBalanceServiceTest}, {@code AttendanceHoursServiceTest}); payroll
 * now derives overtime from Vashandi via {@link VashandiAttendanceClient}.
 */
@ExtendWith(MockitoExtension.class)
class HrPayrollDomainLogicTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000099");

    @Mock private PayrollRunRepository payrollRunRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private DeductionTypeRepository deductionTypeRepository;
    @Mock private VashandiAttendanceClient vashandiAttendanceClient;
    @Mock private HrOutboxWriter outbox;

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

    @Test
    void payrollDefaultsOvertimeToZeroWhenWorkerUnmappedInVashandi() throws Exception {
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
        emp.setProviderId("PRV-X");
        when(employeeRepository.findByTenantIdOrderByStaffNumberAsc(TENANT)).thenReturn(List.of(emp));

        ContractEntity contract = new ContractEntity();
        contract.setEmployeeId(empId);
        contract.setBasicSalary(new BigDecimal("1760"));
        when(contractRepository.findByEmployeeIdOrderByStartDateDesc(empId)).thenReturn(List.of(contract));
        when(deductionTypeRepository.findByTenantIdOrderByNameAsc(TENANT)).thenReturn(List.of());

        // Unmapped / unavailable → empty → overtime defaults to 0 (WARN-and-proceed)
        when(vashandiAttendanceClient.overtimeHours(TENANT, "PRV-X", 2026, 3)).thenReturn(Optional.empty());

        when(payslipRepository.save(any())).thenAnswer(i -> {
            PayslipEntity p = i.getArgument(0);
            if (p.getPayslipId() == null) p.setPayslipId(UUID.randomUUID());
            return p;
        });
        lenient().when(payrollRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        svc.calculate(TENANT, runId);

        ArgumentCaptor<PayslipEntity> captor = ArgumentCaptor.forClass(PayslipEntity.class);
        verify(payslipRepository).save(captor.capture());
        // gross = basic only (no allowances, no overtime)
        assertThat(captor.getValue().getGrossPay()).isEqualByComparingTo("1760.00");
    }
}
