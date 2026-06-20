package zw.gov.mohcc.impilo.hrpayroll.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.hrpayroll.core.PayrollService;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.EmployeeEntity;
import zw.gov.mohcc.impilo.hrpayroll.persistence.repository.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalHrApiContractSurfaceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000011");

    @Mock private EmployeeRepository employeeRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private LeaveBalanceRepository leaveBalanceRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private PayrollRunRepository payrollRunRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private DeductionTypeRepository deductionTypeRepository;
    @Mock private PayrollService payrollService;

    private InternalHrApi api;

    @BeforeEach
    void setUp() {
        api = new InternalHrApi(
                employeeRepository,
                contractRepository,
                leaveTypeRepository,
                leaveRequestRepository,
                leaveBalanceRepository,
                attendanceRepository,
                payrollRunRepository,
                payslipRepository,
                deductionTypeRepository,
                payrollService);
        RequestContextHolder.set(RequestContext.of(
                TENANT.toString(), "national", "req-hr", "corr-hr", null, null, null));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void listEmployeesScopesByTenant() {
        EmployeeEntity employee = new EmployeeEntity();
        when(employeeRepository.findByTenantIdOrderByStaffNumberAsc(TENANT)).thenReturn(List.of(employee));

        List<EmployeeEntity> result = api.listEmployees();

        assertThat(result).containsExactly(employee);
        verify(employeeRepository).findByTenantIdOrderByStaffNumberAsc(TENANT);
    }
}
