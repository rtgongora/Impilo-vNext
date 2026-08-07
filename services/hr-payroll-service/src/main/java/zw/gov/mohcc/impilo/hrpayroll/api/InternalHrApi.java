package zw.gov.mohcc.impilo.hrpayroll.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.hrpayroll.core.PayrollService;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.*;
import zw.gov.mohcc.impilo.hrpayroll.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;

@RestController
public class InternalHrApi {

    /**
     * Actor types permitted to mutate HR/payroll records. HR administration is a
     * back-office (OPERATOR) or platform (SYSTEM) function; clinicians (PROVIDER) and
     * citizens (CITIZEN) must never write payroll/leave/attendance data (G021).
     */
    private static final ActorTypeGuard.Duty WRITE_HR_RECORDS =
            ActorTypeGuard.Duty.of("mutating HR/payroll records", Set.of("OPERATOR", "SYSTEM"));

    private final EmployeeRepository employeeRepository;
    private final ContractRepository contractRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final DeductionTypeRepository deductionTypeRepository;
    private final PayrollService payrollService;

    public InternalHrApi(EmployeeRepository employeeRepository,
                         ContractRepository contractRepository,
                         PayrollRunRepository payrollRunRepository,
                         PayslipRepository payslipRepository,
                         DeductionTypeRepository deductionTypeRepository,
                         PayrollService payrollService) {
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.payslipRepository = payslipRepository;
        this.deductionTypeRepository = deductionTypeRepository;
        this.payrollService = payrollService;
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
        // Already correct and already fail-closed — this gate named only emittable actor types.
        // Migrated for the shared vocabulary and the emittable invariant, deliberately keeping both
        // its exact set (SERVICE is not added: no reason to widen a gate that was right) and its
        // AccessDeniedException, so the response mapping does not change.
        if (!ActorTypeGuard.permits(actorType, WRITE_HR_RECORDS)) {
            throw new AccessDeniedException(
                    "HR/payroll mutations require an OPERATOR or SYSTEM actor; got actorType=" + actorType);
        }
    }

    @GetMapping("/internal/v1/hr/employees")
    public List<EmployeeEntity> listEmployees() {
        return employeeRepository.findByTenantIdOrderByStaffNumberAsc(tenant());
    }

    /**
     * Creates/updates a payroll-financial employee record. Note: the
     * {@code employmentStatus} on the request body is payroll-financial only
     * (payroll runs/contracts) and is NOT the employment-trust source of record —
     * workforce-governance {@code HscEmployment.employmentStatus} is authoritative
     * for trust/eligibility. This endpoint neither emits a trust event nor feeds
     * governance/vashandi trust; it must not be consumed for access decisions.
     */
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

    // Workforce leave + attendance (incl. leave *types*) are owned by Vashandi — the ERP-HR
    // UI reads them from Vashandi via the BFF. hr-payroll keeps the payroll-financial surface.

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
