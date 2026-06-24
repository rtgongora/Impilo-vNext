package zw.gov.mohcc.impilo.hrpayroll.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.hrpayroll.core.AttendanceService;
import zw.gov.mohcc.impilo.hrpayroll.core.LeaveService;
import zw.gov.mohcc.impilo.hrpayroll.core.PayrollService;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.*;
import zw.gov.mohcc.impilo.hrpayroll.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
public class InternalHrApi {

    /**
     * Actor types permitted to mutate HR/payroll records. HR administration is a
     * back-office (OPERATOR) or platform (SYSTEM) function; clinicians (PROVIDER) and
     * citizens (CITIZEN) must never write payroll/leave/attendance data (G021).
     */
    private static final Set<String> WRITE_ACTOR_TYPES = Set.of("OPERATOR", "SYSTEM");

    private final EmployeeRepository employeeRepository;
    private final ContractRepository contractRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final AttendanceRepository attendanceRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final DeductionTypeRepository deductionTypeRepository;
    private final PayrollService payrollService;
    private final LeaveService leaveService;
    private final AttendanceService attendanceService;

    public InternalHrApi(EmployeeRepository employeeRepository,
                         ContractRepository contractRepository,
                         LeaveTypeRepository leaveTypeRepository,
                         LeaveRequestRepository leaveRequestRepository,
                         LeaveBalanceRepository leaveBalanceRepository,
                         AttendanceRepository attendanceRepository,
                         PayrollRunRepository payrollRunRepository,
                         PayslipRepository payslipRepository,
                         DeductionTypeRepository deductionTypeRepository,
                         PayrollService payrollService,
                         LeaveService leaveService,
                         AttendanceService attendanceService) {
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.attendanceRepository = attendanceRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.payslipRepository = payslipRepository;
        this.deductionTypeRepository = deductionTypeRepository;
        this.payrollService = payrollService;
        this.leaveService = leaveService;
        this.attendanceService = attendanceService;
    }

    private UUID tenant() {
        return UUID.fromString(RequestContextHolder.require().tenantId());
    }

    /**
     * Method-level authorization for mutating HR/payroll routes (G021). Rejects
     * EXTERNAL callers and any actor type outside the back-office/platform set with
     * a 403 (Spring maps {@link AccessDeniedException}).
     */
    private void requireHrWriteAuthority() {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() == AccessMode.EXTERNAL) {
            throw new AccessDeniedException("HR/payroll mutations are not permitted for EXTERNAL callers");
        }
        String actorType = ctx.actorType();
        if (actorType == null || !WRITE_ACTOR_TYPES.contains(actorType.toUpperCase())) {
            throw new AccessDeniedException(
                    "HR/payroll mutations require an OPERATOR or SYSTEM actor; got actorType=" + actorType);
        }
    }

    @GetMapping("/internal/v1/hr/employees")
    public List<EmployeeEntity> listEmployees() {
        return employeeRepository.findByTenantIdOrderByStaffNumberAsc(tenant());
    }

    @PostMapping("/internal/v1/hr/employees")
    public EmployeeEntity createEmployee(@RequestBody EmployeeEntity e) {
        requireHrWriteAuthority();
        e.setTenantId(tenant());
        return employeeRepository.save(e);
    }

    @GetMapping("/internal/v1/hr/employees/{id}")
    public ResponseEntity<EmployeeEntity> getEmployee(@PathVariable UUID id) {
        return employeeRepository.findById(id).filter(x -> x.getTenantId().equals(tenant()))
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/internal/v1/hr/contracts")
    public List<ContractEntity> listContracts(@RequestParam("employee_id") UUID employeeId) {
        return contractRepository.findByEmployeeIdOrderByStartDateDesc(employeeId);
    }

    @PostMapping("/internal/v1/hr/contracts")
    public ContractEntity createContract(@RequestBody ContractEntity c) {
        requireHrWriteAuthority();
        return contractRepository.save(c);
    }

    @GetMapping("/internal/v1/hr/leave/types")
    public List<LeaveTypeEntity> leaveTypes() {
        return leaveTypeRepository.findByTenantIdOrderByNameAsc(tenant());
    }

    @PostMapping("/internal/v1/hr/leave/types")
    public LeaveTypeEntity createLeaveType(@RequestBody LeaveTypeEntity t) {
        requireHrWriteAuthority();
        t.setTenantId(tenant());
        return leaveTypeRepository.save(t);
    }

    @GetMapping("/internal/v1/hr/leave/requests")
    public List<LeaveRequestEntity> leaveRequests(@RequestParam("employee_id") UUID employeeId) {
        return leaveRequestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId);
    }

    @PostMapping("/internal/v1/hr/leave/requests")
    public LeaveRequestEntity createLeaveRequest(@RequestBody LeaveRequestEntity r) throws Exception {
        requireHrWriteAuthority();
        return leaveService.requestLeave(tenant(), r);
    }

    @GetMapping("/internal/v1/hr/leave/balances")
    public List<LeaveBalanceEntity> leaveBalances(@RequestParam("employee_id") UUID employeeId,
                                                  @RequestParam int fiscalYear) {
        return leaveBalanceRepository.findByEmployeeIdAndFiscalYear(employeeId, fiscalYear);
    }

    @PostMapping("/internal/v1/hr/leave/balances")
    public LeaveBalanceEntity upsertBalance(@RequestBody LeaveBalanceEntity b) {
        requireHrWriteAuthority();
        return leaveBalanceRepository.save(b);
    }

    @GetMapping("/internal/v1/hr/attendance")
    public List<AttendanceRecordEntity> attendance(@RequestParam("employee_id") UUID employeeId) {
        return attendanceRepository.findByEmployeeIdOrderByShiftDateDesc(employeeId);
    }

    @PostMapping("/internal/v1/hr/attendance")
    public AttendanceRecordEntity createAttendance(@RequestBody AttendanceRecordEntity a) {
        requireHrWriteAuthority();
        return attendanceService.record(a);
    }

    @GetMapping("/internal/v1/hr/payroll/runs")
    public List<PayrollRunEntity> payrollRuns() {
        return payrollRunRepository.findByTenantIdOrderByCreatedAtDesc(tenant());
    }

    @PostMapping("/internal/v1/hr/payroll/runs")
    public PayrollRunEntity createPayrollRun(@RequestBody JsonNode body) {
        requireHrWriteAuthority();
        PayrollRunEntity r = new PayrollRunEntity();
        r.setTenantId(tenant());
        r.setPeriodMonth(body.path("periodMonth").asInt());
        r.setPeriodYear(body.path("periodYear").asInt());
        r.setStatus("DRAFT");
        return payrollRunRepository.save(r);
    }

    @PostMapping("/internal/v1/hr/payroll/runs/{id}/calculate")
    public PayrollRunEntity calculate(@PathVariable UUID id) throws Exception {
        requireHrWriteAuthority();
        return payrollService.calculate(tenant(), id);
    }

    @PostMapping("/internal/v1/hr/payroll/runs/{id}/pay")
    public PayrollRunEntity pay(@PathVariable UUID id, @RequestBody(required = false) JsonNode body) throws Exception {
        requireHrWriteAuthority();
        String by = body != null && body.hasNonNull("approvedBy") ? body.get("approvedBy").asText() : "api";
        return payrollService.markPaid(tenant(), id, by);
    }

    @GetMapping("/internal/v1/hr/payroll/payslips")
    public List<PayslipEntity> payslips(@RequestParam("run_id") UUID runId) {
        return payslipRepository.findByRunId(runId);
    }

    @GetMapping("/internal/v1/hr/deductions")
    public List<DeductionTypeEntity> deductions() {
        return deductionTypeRepository.findByTenantIdOrderByNameAsc(tenant());
    }

    @PostMapping("/internal/v1/hr/deductions")
    public DeductionTypeEntity createDeduction(@RequestBody DeductionTypeEntity d) {
        requireHrWriteAuthority();
        d.setTenantId(tenant());
        return deductionTypeRepository.save(d);
    }
}
