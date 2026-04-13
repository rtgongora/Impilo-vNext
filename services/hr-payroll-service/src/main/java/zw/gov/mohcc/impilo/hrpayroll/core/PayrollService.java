package zw.gov.mohcc.impilo.hrpayroll.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.*;
import zw.gov.mohcc.impilo.hrpayroll.persistence.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PayrollService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final EmployeeRepository employeeRepository;
    private final ContractRepository contractRepository;
    private final DeductionTypeRepository deductionTypeRepository;
    private final HrOutboxWriter hrOutboxWriter;

    public PayrollService(PayrollRunRepository payrollRunRepository,
                          PayslipRepository payslipRepository,
                          EmployeeRepository employeeRepository,
                          ContractRepository contractRepository,
                          DeductionTypeRepository deductionTypeRepository,
                          HrOutboxWriter hrOutboxWriter) {
        this.payrollRunRepository = payrollRunRepository;
        this.payslipRepository = payslipRepository;
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
        this.deductionTypeRepository = deductionTypeRepository;
        this.hrOutboxWriter = hrOutboxWriter;
    }

    @Transactional
    public PayrollRunEntity calculate(UUID tenantId, UUID runId) throws Exception {
        PayrollRunEntity run = payrollRunRepository.findById(runId)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow();
        if (!"DRAFT".equals(run.getStatus())) {
            throw new IllegalStateException("Run not in DRAFT");
        }
        payslipRepository.deleteAll(payslipRepository.findByRunId(runId));
        List<DeductionTypeEntity> deds = deductionTypeRepository.findByTenantIdOrderByNameAsc(tenantId);
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDed = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        for (EmployeeEntity emp : employeeRepository.findByTenantIdOrderByStaffNumberAsc(tenantId)) {
            if (!"ACTIVE".equals(emp.getEmploymentStatus())) {
                continue;
            }
            ContractEntity c = contractRepository.findByEmployeeIdOrderByStartDateDesc(emp.getEmployeeId())
                    .stream().findFirst().orElse(null);
            if (c == null) {
                continue;
            }
            BigDecimal gross = c.getBasicSalary() != null ? c.getBasicSalary() : BigDecimal.ZERO;
            BigDecimal tax = BigDecimal.ZERO;
            BigDecimal pension = BigDecimal.ZERO;
            BigDecimal other = BigDecimal.ZERO;
            for (DeductionTypeEntity d : deds) {
                BigDecimal amt = applyDeduction(gross, d);
                switch (d.getCategory()) {
                    case "TAX" -> tax = tax.add(amt);
                    case "PENSION" -> pension = pension.add(amt);
                    default -> other = other.add(amt);
                }
            }
            BigDecimal net = gross.subtract(tax).subtract(pension).subtract(other);
            PayslipEntity p = new PayslipEntity();
            p.setRunId(runId);
            p.setEmployeeId(emp.getEmployeeId());
            p.setBasicSalary(gross);
            p.setGrossPay(gross);
            p.setTax(tax);
            p.setPension(pension);
            p.setOtherDeductions(other);
            p.setNetPay(net);
            payslipRepository.save(p);
            totalGross = totalGross.add(gross);
            totalDed = totalDed.add(tax).add(pension).add(other);
            totalNet = totalNet.add(net);
            hrOutboxWriter.publish(tenantId, "PAYSLIP", p.getPayslipId().toString(), "payslip", "generated",
                    "hr:payslip:" + p.getPayslipId(),
                    Map.of("tenantId", tenantId.toString(), "payslipId", p.getPayslipId().toString(),
                            "employeeId", emp.getEmployeeId().toString(), "grossPay", gross, "netPay", net,
                            "tax", tax, "pension", pension));
        }
        run.setTotalGross(totalGross);
        run.setTotalDeductions(totalDed);
        run.setTotalNet(totalNet);
        run.setStatus("CALCULATED");
        payrollRunRepository.save(run);
        hrOutboxWriter.publish(tenantId, "PAYROLL_RUN", runId.toString(), "payroll_run", "completed",
                "hr:payroll_run:" + runId + ":calculated",
                Map.of("tenantId", tenantId.toString(), "runId", runId.toString(), "status", "CALCULATED",
                        "totalNet", totalNet));
        return run;
    }

    private static BigDecimal applyDeduction(BigDecimal gross, DeductionTypeEntity d) {
        if ("FIXED".equals(d.getCalculationMethod()) && d.getRate() != null) {
            return d.getRate();
        }
        if ("PERCENTAGE".equals(d.getCalculationMethod()) && d.getRate() != null) {
            return gross.multiply(d.getRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    @Transactional
    public PayrollRunEntity markPaid(UUID tenantId, UUID runId, String approvedBy) throws Exception {
        PayrollRunEntity run = payrollRunRepository.findById(runId)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow();
        run.setStatus("PAID");
        run.setApprovedBy(approvedBy);
        run.setPaidAt(OffsetDateTime.now());
        payrollRunRepository.save(run);
        hrOutboxWriter.publish(tenantId, "PAYROLL_RUN", runId.toString(), "payroll_run", "completed",
                "hr:payroll_run:" + runId + ":paid",
                Map.of("tenantId", tenantId.toString(), "runId", runId.toString(), "status", "PAID"));
        return run;
    }
}
