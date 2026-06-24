package zw.gov.mohcc.impilo.hrpayroll.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.hrpayroll.core.PayrollService;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.EmployeeEntity;
import zw.gov.mohcc.impilo.hrpayroll.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalHrApiContractSurfaceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000011");

    @Mock private EmployeeRepository employeeRepository;
    @Mock private ContractRepository contractRepository;
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
        TrustContextHolder.clear();
    }

    private void trustAs(String actorType, AccessMode mode) {
        TrustContextHolder.set(new TrustContext(
                TENANT, "actor-1", actorType, "OPERATIONS", null, UUID.randomUUID(),
                null, null, null, mode));
    }

    @Test
    void listEmployeesScopesByTenant() {
        EmployeeEntity employee = new EmployeeEntity();
        when(employeeRepository.findByTenantIdOrderByStaffNumberAsc(TENANT)).thenReturn(List.of(employee));

        List<EmployeeEntity> result = api.listEmployees();

        assertThat(result).containsExactly(employee);
        verify(employeeRepository).findByTenantIdOrderByStaffNumberAsc(TENANT);
    }

    // ── G021: method-level authz on mutating routes ──

    @Test
    void createEmployeeAllowedForOperator() {
        trustAs("OPERATOR", AccessMode.INTERNAL);
        EmployeeEntity e = new EmployeeEntity();
        when(employeeRepository.save(any())).thenReturn(e);

        assertThat(api.createEmployee(e)).isSameAs(e);
        verify(employeeRepository).save(e);
    }

    @Test
    void createEmployeeDeniedForCitizen() {
        trustAs("CITIZEN", AccessMode.INTERNAL);
        assertThatThrownBy(() -> api.createEmployee(new EmployeeEntity()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void createEmployeeDeniedForExternalMode() {
        trustAs("OPERATOR", AccessMode.EXTERNAL);
        assertThatThrownBy(() -> api.createEmployee(new EmployeeEntity()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void createContractDeniedForProvider() {
        trustAs("PROVIDER", AccessMode.INTERNAL);
        assertThatThrownBy(() -> api.createContract(new zw.gov.mohcc.impilo.hrpayroll.persistence.entity.ContractEntity()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(contractRepository);
    }
}
