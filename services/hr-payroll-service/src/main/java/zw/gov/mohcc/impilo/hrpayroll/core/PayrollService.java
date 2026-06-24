package zw.gov.mohcc.impilo.hrpayroll.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.*;
import zw.gov.mohcc.impilo.hrpayroll.persistence.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PayrollService {

    /** Standard paid hours in a month (22 working days x 8h) — basis for the hourly rate. */
    private static final BigDecimal STANDARD_MONTHLY_HOURS = new BigDecimal("176");
    /** Overtime is paid at 1.5x the ordinary hourly rate. */
    private static final BigDecimal OVERTIME_MULTIPLIER = new BigDecimal("1.5");

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final EmployeeRepository employeeRepository;
    private final ContractRepository contractRepository;
    private final DeductionTypeRepository deductionTypeRepository;
    private final AttendanceRepository attendanceRepository;
    private final ObjectMapper objectMapper;
    private final HrOutboxWriter hrOutboxWriter;

    public PayrollService(PayrollRunRepository payrollRunRepository,
                          PayslipRepository payslipRepository,
                          EmployeeRepository employeeRepository,
                          ContractRepository contractRepository,
                          DeductionTypeRepository deductionTypeRepository,
                          AttendanceRepository attendanceRepository,
                          ObjectMapper objectMapper,
                          HrOutboxWriter hrOutboxWriter) {
        this.payrollRunRepository = payrollRunRepository;
        this.payslipRepository = payslipRepository;
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
        this.deductionTypeRepository = deductionTypeRepository;
        this.attendanceRepository = attendanceRepository;
        this.objectMapper = objectMapper;
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
            BigDecimal basic = c.getBasicSalary() != null ? c.getBasicSalary() : BigDecimal.ZERO;
            BigDecimal allowances = sumAllowances(c.getAllowancesJson());
            BigDecimal overtimeHours = overtimeHoursForPeriod(emp.getEmployeeId(), run);
            BigDecimal overtimePay = overtimePay(basic, overtimeHours);
            // Gross earnings = basic + allowances + overtime (was basic-only — G043).
            BigDecimal gross = basic.add(allowances).add(overtimePay).setScale(2, RoundingMode.HALF_UP);
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
            p.setBasicSalary(basic);
            p.setAllowancesJson(earningsBreakdownJson(allowances, overtimeHours, overtimePay));
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

    /** Sum the numeric values of a contract's {@code allowancesJson} object ({"housing":100,...}). */
    private BigDecimal sumAllowances(String allowancesJson) {
        if (allowancesJson == null || allowancesJson.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            JsonNode node = objectMapper.readTree(allowancesJson);
            if (node == null || !node.isObject()) {
                return BigDecimal.ZERO;
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
                JsonNode v = it.next();
                if (v.isNumber()) {
                    sum = sum.add(v.decimalValue());
                } else if (v.isTextual()) {
                    try {
                        sum = sum.add(new BigDecimal(v.asText().trim()));
                    } catch (NumberFormatException ignored) {
                        // non-numeric allowance entry — skip
                    }
                }
            }
            return sum;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /** Total overtime hours recorded for the employee within the run's calendar month. */
    private BigDecimal overtimeHoursForPeriod(UUID employeeId, PayrollRunEntity run) {
        LocalDate start = LocalDate.of(run.getPeriodYear(), run.getPeriodMonth(), 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        BigDecimal total = BigDecimal.ZERO;
        for (AttendanceRecordEntity a : attendanceRepository
                .findByEmployeeIdAndShiftDateBetween(employeeId, start, end)) {
            if (a.getOvertimeHours() != null) {
                total = total.add(a.getOvertimeHours());
            }
        }
        return total;
    }

    /** Overtime pay = overtimeHours x (basic / standard monthly hours) x 1.5. */
    private static BigDecimal overtimePay(BigDecimal basic, BigDecimal overtimeHours) {
        if (basic == null || basic.signum() <= 0 || overtimeHours == null || overtimeHours.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal hourlyRate = basic.divide(STANDARD_MONTHLY_HOURS, 4, RoundingMode.HALF_UP);
        return overtimeHours.multiply(hourlyRate).multiply(OVERTIME_MULTIPLIER)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Auditable earnings breakdown stored on the payslip. */
    private String earningsBreakdownJson(BigDecimal allowances, BigDecimal overtimeHours, BigDecimal overtimePay) {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("allowances", allowances);
        breakdown.put("overtimeHours", overtimeHours);
        breakdown.put("overtimePay", overtimePay);
        try {
            return objectMapper.writeValueAsString(breakdown);
        } catch (Exception e) {
            return "{}";
        }
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
