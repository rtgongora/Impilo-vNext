package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.costa.domain.enums.*;
import zw.gov.mohcc.impilo.costa.engine.CostEngine;
import zw.gov.mohcc.impilo.costa.engine.CostEngineRegistry;
import zw.gov.mohcc.impilo.costa.engine.CostResult;
import zw.gov.mohcc.impilo.costa.exemptions.BillSplitResult;
import zw.gov.mohcc.impilo.costa.exemptions.ExemptionEngine;
import zw.gov.mohcc.impilo.costa.rules.ChargingRuleEngine;
import zw.gov.mohcc.impilo.costa.rules.RuleContext;
import zw.gov.mohcc.impilo.costa.rules.RuleResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class EstimateService {

    private static final Logger log = LoggerFactory.getLogger(EstimateService.class);

    private final CostEngineRegistry costEngineRegistry;
    private final ChargingRuleEngine chargingRuleEngine;
    private final ExemptionEngine exemptionEngine;
    private final ObjectMapper objectMapper;

    public EstimateService(CostEngineRegistry costEngineRegistry,
                           ChargingRuleEngine chargingRuleEngine,
                           ExemptionEngine exemptionEngine,
                           ObjectMapper objectMapper) {
        this.costEngineRegistry = costEngineRegistry;
        this.chargingRuleEngine = chargingRuleEngine;
        this.exemptionEngine = exemptionEngine;
        this.objectMapper = objectMapper;
    }

    public EstimateResult estimate(UUID tenantId, UUID facilityId,
                                   List<EstimateLineInput> items,
                                   String exemptionCategory,
                                   Long insurancePlanId,
                                   Map<String, Object> context) {

        String facilityCategory = (String) context.getOrDefault("facility_category", null);
        String patientCategory = (String) context.getOrDefault("patient_category", null);
        String encounterType = (String) context.getOrDefault("encounter_type", "OUTPATIENT");
        boolean isEmergency = Boolean.parseBoolean(context.getOrDefault("is_emergency", "false").toString());
        boolean isAdmitted = Boolean.parseBoolean(context.getOrDefault("is_admitted", "false").toString());

        CostMethodType defaultMethod = CostMethodType.TARIFF;
        if (context.containsKey("cost_method")) {
            defaultMethod = CostMethodType.valueOf(context.get("cost_method").toString());
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalCharge = BigDecimal.ZERO;
        List<EstimateLineResult> lines = new ArrayList<>();

        for (EstimateLineInput item : items) {
            CostMethodType method = item.costMethod() != null ? item.costMethod() : defaultMethod;
            CostEngine engine = costEngineRegistry.resolveOrDefault(method, CostMethodType.TARIFF);

            Map<String, Object> itemContext = new HashMap<>(context);
            if (item.extras() != null) itemContext.putAll(item.extras());

            CostResult costResult = engine.compute(tenantId, facilityId,
                    item.msikaCode(), item.qty(), itemContext);

            // Apply charging rules
            RuleContext ruleCtx = new RuleContext(tenantId, facilityId, facilityCategory,
                    patientCategory, item.msikaCode(), item.kind(),
                    item.qty(), costResult.totalCost(), encounterType,
                    LocalDateTime.now(), isEmergency, isAdmitted, itemContext);

            RuleResult ruleResult = chargingRuleEngine.evaluate(ruleCtx);

            totalCost = totalCost.add(costResult.totalCost());
            totalCharge = totalCharge.add(ruleResult.chargeAmount());

            lines.add(new EstimateLineResult(
                    item.msikaCode(), item.description(), item.kind(),
                    item.qty(), costResult.unitCost(), costResult.totalCost(),
                    ruleResult.chargeAmount(), costResult.methodUsed(),
                    costResult.trace(), ruleResult.trace()));
        }

        // Split across parties
        BillSplitResult split = exemptionEngine.split(tenantId, totalCharge,
                exemptionCategory, insurancePlanId, context);

        Map<String, Object> traceSummary = new LinkedHashMap<>();
        traceSummary.put("items_count", items.size());
        traceSummary.put("total_cost", totalCost);
        traceSummary.put("total_charge", totalCharge);
        traceSummary.put("split", split.trace());

        return new EstimateResult(totalCost, totalCharge, split.patientPayable(),
                split.insurerPayable(), split.subsidyPayable(), split.writeOff(),
                split.totalDiscount(), lines, traceSummary);
    }

    public record EstimateLineInput(
            String msikaCode,
            String description,
            BillLineKind kind,
            BigDecimal qty,
            CostMethodType costMethod,
            Map<String, Object> extras
    ) {}

    public record EstimateLineResult(
            String msikaCode, String description, BillLineKind kind,
            BigDecimal qty, BigDecimal unitCost, BigDecimal costAmount,
            BigDecimal chargeAmount, CostMethodType costMethodUsed,
            Map<String, Object> costTrace, Map<String, Object> chargeTrace
    ) {}

    public record EstimateResult(
            BigDecimal totalCost, BigDecimal totalCharge,
            BigDecimal patientPayable, BigDecimal insurerPayable,
            BigDecimal subsidyPayable, BigDecimal writeOff,
            BigDecimal totalDiscount,
            List<EstimateLineResult> lines,
            Map<String, Object> trace
    ) {}
}
