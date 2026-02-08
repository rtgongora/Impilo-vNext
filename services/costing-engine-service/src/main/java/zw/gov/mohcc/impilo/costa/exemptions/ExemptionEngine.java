package zw.gov.mohcc.impilo.costa.exemptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.costa.domain.entity.ExemptionRuleEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.InsurancePlanEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ExemptionCategory;
import zw.gov.mohcc.impilo.costa.domain.repository.ExemptionRuleRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InsurancePlanRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Computes bill splitting across patient, insurer, subsidy, and write-off parties.
 * Applies exemptions first, then insurance coverage, then any subsidy programs.
 */
@Service
public class ExemptionEngine {

    private static final Logger log = LoggerFactory.getLogger(ExemptionEngine.class);

    private final ExemptionRuleRepository exemptionRuleRepository;
    private final InsurancePlanRepository insurancePlanRepository;
    private final ObjectMapper objectMapper;

    public ExemptionEngine(ExemptionRuleRepository exemptionRuleRepository,
                           InsurancePlanRepository insurancePlanRepository,
                           ObjectMapper objectMapper) {
        this.exemptionRuleRepository = exemptionRuleRepository;
        this.insurancePlanRepository = insurancePlanRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Split a bill total across responsible parties.
     *
     * @param tenantId          tenant context
     * @param totalCharge       the total charge amount to split
     * @param exemptionCategory the patient's exemption category (nullable)
     * @param insurancePlanId   the insurance plan ID (nullable)
     * @param context           additional context for rule evaluation
     */
    public BillSplitResult split(UUID tenantId, BigDecimal totalCharge,
                                 String exemptionCategory, Long insurancePlanId,
                                 Map<String, Object> context) {

        BigDecimal remaining = totalCharge;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal patientPayable;
        BigDecimal insurerPayable = BigDecimal.ZERO;
        BigDecimal subsidyPayable = BigDecimal.ZERO;
        BigDecimal writeOff = BigDecimal.ZERO;
        List<BillSplitResult.PartyAllocation> allocations = new ArrayList<>();
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("total_charge", totalCharge);

        // Step 1: Apply exemptions
        if (exemptionCategory != null && !exemptionCategory.isBlank()) {
            try {
                ExemptionCategory cat = ExemptionCategory.valueOf(exemptionCategory);
                List<ExemptionRuleEntity> exemptions = exemptionRuleRepository.findEffective(
                        tenantId, cat, LocalDate.now());

                if (!exemptions.isEmpty()) {
                    ExemptionRuleEntity exemption = exemptions.get(0);
                    BigDecimal discountPct = exemption.getDiscountPct();
                    BigDecimal exemptionDiscount = remaining.multiply(discountPct)
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                    // Apply cap if set
                    if (exemption.getMaxAmount() != null &&
                            exemptionDiscount.compareTo(exemption.getMaxAmount()) > 0) {
                        exemptionDiscount = exemption.getMaxAmount();
                    }

                    remaining = remaining.subtract(exemptionDiscount);
                    totalDiscount = totalDiscount.add(exemptionDiscount);

                    Map<String, Object> exemptionTrace = new LinkedHashMap<>();
                    exemptionTrace.put("category", exemptionCategory);
                    exemptionTrace.put("rule_id", exemption.getId());
                    exemptionTrace.put("discount_pct", discountPct);
                    exemptionTrace.put("discount_amount", exemptionDiscount);
                    exemptionTrace.put("max_amount", exemption.getMaxAmount());

                    // If 100% exemption, allocate as write-off
                    if (discountPct.compareTo(new BigDecimal("100")) >= 0) {
                        writeOff = writeOff.add(exemptionDiscount);
                        allocations.add(new BillSplitResult.PartyAllocation(
                                "WRITE_OFF", exemptionCategory, exemptionDiscount,
                                new String[]{"EXEMPTION_" + exemptionCategory},
                                exemptionTrace));
                    } else {
                        subsidyPayable = subsidyPayable.add(exemptionDiscount);
                        allocations.add(new BillSplitResult.PartyAllocation(
                                "SUBSIDY", "GOVT_EXEMPTION_" + exemptionCategory, exemptionDiscount,
                                new String[]{"EXEMPTION_" + exemptionCategory},
                                exemptionTrace));
                    }

                    trace.put("exemption", exemptionTrace);
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid exemption category: {}", exemptionCategory);
            }
        }

        // Step 2: Apply insurance
        if (insurancePlanId != null && remaining.compareTo(BigDecimal.ZERO) > 0) {
            Optional<InsurancePlanEntity> planOpt = insurancePlanRepository.findById(insurancePlanId);
            if (planOpt.isPresent()) {
                InsurancePlanEntity plan = planOpt.get();

                // Apply deductible
                BigDecimal afterDeductible = remaining;
                BigDecimal deductibleApplied = BigDecimal.ZERO;
                if (plan.getDeductible().compareTo(BigDecimal.ZERO) > 0) {
                    deductibleApplied = remaining.min(plan.getDeductible());
                    afterDeductible = remaining.subtract(deductibleApplied);
                }

                // Calculate insurer share
                BigDecimal coveragePct = plan.getCoveragePct();
                BigDecimal insurerShare = afterDeductible.multiply(coveragePct)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                // Apply annual cap
                if (plan.getAnnualCap() != null && insurerShare.compareTo(plan.getAnnualCap()) > 0) {
                    insurerShare = plan.getAnnualCap();
                }

                // Copay (patient's share of the insured portion)
                BigDecimal copay = BigDecimal.ZERO;
                if (plan.getCopayFixed() != null && plan.getCopayFixed().compareTo(BigDecimal.ZERO) > 0) {
                    copay = plan.getCopayFixed();
                } else if (plan.getCopayPct().compareTo(BigDecimal.ZERO) > 0) {
                    copay = afterDeductible.multiply(plan.getCopayPct())
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                }

                // Adjust: insurer pays coverage minus copay
                insurerPayable = insurerShare.subtract(copay).max(BigDecimal.ZERO);
                remaining = remaining.subtract(insurerPayable);

                Map<String, Object> insuranceTrace = new LinkedHashMap<>();
                insuranceTrace.put("plan_id", plan.getId());
                insuranceTrace.put("plan_code", plan.getPlanCode());
                insuranceTrace.put("insurer_name", plan.getInsurerName());
                insuranceTrace.put("coverage_pct", coveragePct);
                insuranceTrace.put("deductible_applied", deductibleApplied);
                insuranceTrace.put("insurer_share", insurerPayable);
                insuranceTrace.put("copay", copay);
                insuranceTrace.put("prior_auth_required", plan.isPriorAuthRequired());

                allocations.add(new BillSplitResult.PartyAllocation(
                        "INSURER", plan.getPlanCode(), insurerPayable,
                        new String[]{"INSURANCE_" + plan.getPlanCode()},
                        insuranceTrace));
                trace.put("insurance", insuranceTrace);
            }
        }

        // Step 3: Patient pays the remainder
        patientPayable = remaining.max(BigDecimal.ZERO);
        if (patientPayable.compareTo(BigDecimal.ZERO) > 0) {
            allocations.add(new BillSplitResult.PartyAllocation(
                    "PATIENT", null, patientPayable,
                    new String[]{"PATIENT_RESPONSIBILITY"},
                    Map.of("amount", patientPayable)));
        }

        BigDecimal totalPayable = patientPayable.add(insurerPayable).add(subsidyPayable);
        trace.put("patient_payable", patientPayable);
        trace.put("insurer_payable", insurerPayable);
        trace.put("subsidy_payable", subsidyPayable);
        trace.put("write_off", writeOff);
        trace.put("total_discount", totalDiscount);

        return new BillSplitResult(totalPayable, patientPayable, insurerPayable,
                subsidyPayable, writeOff, totalDiscount, allocations, trace);
    }
}
