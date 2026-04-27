package zw.gov.mohcc.impilo.costa.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.costa.domain.entity.ChargingRulesetEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.ChargingRulesetRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Charging rules engine. Evaluates JSON-based rules to transform cost into charges.
 *
 * Rule JSON structure:
 * [
 *   {
 *     "name": "Emergency surcharge",
 *     "priority": 10,
 *     "billing_timing_mode": "POINT_OF_CARE",
 *     "conditions": {
 *       "is_emergency": true,
 *       "encounter_type": "EMERGENCY"
 *     },
 *     "action": {
 *       "type": "MARKUP_PCT",
 *       "value": 25.0
 *     }
 *   },
 *   {
 *     "name": "Admitted consultation waiver",
 *     "priority": 20,
 *     "conditions": {
 *       "is_admitted": true,
 *       "kind": "FEE"
 *     },
 *     "action": {
 *       "type": "EXCLUDE"
 *     }
 *   },
 *   {
 *     "name": "Private patient markup",
 *     "priority": 5,
 *     "conditions": {
 *       "patient_category": "PRIVATE"
 *     },
 *     "action": {
 *       "type": "MARKUP_PCT",
 *       "value": 50.0
 *     }
 *   }
 * ]
 *
 * Action types: MARKUP_PCT, MARKUP_FIXED, DISCOUNT_PCT, DISCOUNT_FIXED,
 *               SET_PRICE, EXCLUDE, BUNDLE
 */
@Service
public class ChargingRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(ChargingRuleEngine.class);

    private final ChargingRulesetRepository rulesetRepository;
    private final ObjectMapper objectMapper;

    public ChargingRuleEngine(ChargingRulesetRepository rulesetRepository,
                              ObjectMapper objectMapper) {
        this.rulesetRepository = rulesetRepository;
        this.objectMapper = objectMapper;
    }

    public RuleResult evaluate(RuleContext ctx) {
        List<ChargingRulesetEntity> rulesets = rulesetRepository.findEffective(
                ctx.tenantId(), LocalDate.now());

        if (rulesets.isEmpty()) {
            return RuleResult.passThrough(ctx.costAmount());
        }

        // Merge all rules from effective rulesets, sort by priority (lower = higher priority)
        List<Map<String, Object>> allRules = new ArrayList<>();
        for (ChargingRulesetEntity ruleset : rulesets) {
            try {
                List<Map<String, Object>> rules = objectMapper.readValue(
                        ruleset.getRules(), new TypeReference<>() {});
                for (Map<String, Object> rule : rules) {
                    rule.put("_ruleset_id", ruleset.getId());
                    rule.put("_ruleset_name", ruleset.getName());
                }
                allRules.addAll(rules);
            } catch (Exception e) {
                log.error("Failed to parse rules from ruleset {}: {}", ruleset.getId(), e.getMessage());
            }
        }

        allRules.sort(Comparator.comparingInt(r ->
                r.containsKey("priority") ? ((Number) r.get("priority")).intValue() : 100));

        BigDecimal chargeAmount = ctx.costAmount();
        BigDecimal totalMarkupPct = BigDecimal.ZERO;
        BigDecimal totalDiscountPct = BigDecimal.ZERO;
        boolean excluded = false;
        boolean bundled = false;
        List<String> appliedRules = new ArrayList<>();
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("initial_cost", ctx.costAmount());
        List<Map<String, Object>> ruleApplications = new ArrayList<>();

        for (Map<String, Object> rule : allRules) {
            if (excluded) break;

            @SuppressWarnings("unchecked")
            Map<String, Object> conditions = (Map<String, Object>) rule.getOrDefault("conditions", Map.of());

            if (!matchesConditions(conditions, ctx)) continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> action = (Map<String, Object>) rule.getOrDefault("action", Map.of());
            String actionType = (String) action.getOrDefault("type", "NONE");
            String ruleName = (String) rule.getOrDefault("name", "unnamed");

            Map<String, Object> application = new LinkedHashMap<>();
            application.put("rule", ruleName);
            application.put("action", actionType);

            switch (actionType) {
                case "MARKUP_PCT" -> {
                    BigDecimal pct = new BigDecimal(action.get("value").toString());
                    BigDecimal markup = chargeAmount.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    chargeAmount = chargeAmount.add(markup);
                    totalMarkupPct = totalMarkupPct.add(pct);
                    application.put("markup_pct", pct);
                    application.put("markup_amount", markup);
                    application.put("new_charge", chargeAmount);
                }
                case "MARKUP_FIXED" -> {
                    BigDecimal fixed = new BigDecimal(action.get("value").toString());
                    chargeAmount = chargeAmount.add(fixed);
                    application.put("markup_fixed", fixed);
                    application.put("new_charge", chargeAmount);
                }
                case "DISCOUNT_PCT" -> {
                    BigDecimal pct = new BigDecimal(action.get("value").toString());
                    BigDecimal discount = chargeAmount.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    chargeAmount = chargeAmount.subtract(discount);
                    totalDiscountPct = totalDiscountPct.add(pct);
                    application.put("discount_pct", pct);
                    application.put("discount_amount", discount);
                    application.put("new_charge", chargeAmount);
                }
                case "DISCOUNT_FIXED" -> {
                    BigDecimal fixed = new BigDecimal(action.get("value").toString());
                    chargeAmount = chargeAmount.subtract(fixed);
                    application.put("discount_fixed", fixed);
                    application.put("new_charge", chargeAmount);
                }
                case "SET_PRICE" -> {
                    BigDecimal price = new BigDecimal(action.get("value").toString());
                    chargeAmount = price.multiply(ctx.qty());
                    application.put("set_price", price);
                    application.put("new_charge", chargeAmount);
                }
                case "EXCLUDE" -> {
                    excluded = true;
                    chargeAmount = BigDecimal.ZERO;
                    application.put("excluded", true);
                }
                case "BUNDLE" -> {
                    bundled = true;
                    application.put("bundled", true);
                    if (action.containsKey("bundle_price")) {
                        chargeAmount = new BigDecimal(action.get("bundle_price").toString());
                    }
                }
                default -> {
                    application.put("skipped", "unknown action type");
                    continue;
                }
            }

            appliedRules.add(ruleName);
            ruleApplications.add(application);
        }

        // Floor at zero
        if (chargeAmount.compareTo(BigDecimal.ZERO) < 0) {
            chargeAmount = BigDecimal.ZERO;
        }

        trace.put("rules_evaluated", allRules.size());
        trace.put("rules_applied", ruleApplications);
        trace.put("final_charge", chargeAmount);

        return new RuleResult(chargeAmount, totalMarkupPct, totalDiscountPct,
                bundled, excluded, appliedRules, trace);
    }

    private boolean matchesConditions(Map<String, Object> conditions, RuleContext ctx) {
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String key = entry.getKey();
            Object expected = entry.getValue();

            boolean matches = switch (key) {
                case "encounter_type" -> expected.toString().equals(ctx.encounterType());
                case "kind" -> expected.toString().equals(ctx.kind().name());
                case "patient_category" -> expected.toString().equals(ctx.patientCategory());
                case "facility_category" -> expected.toString().equals(ctx.facilityCategory());
                case "msika_code" -> expected.toString().equals(ctx.msikaCode());
                case "is_emergency" -> Boolean.parseBoolean(expected.toString()) == ctx.isEmergency();
                case "is_admitted" -> Boolean.parseBoolean(expected.toString()) == ctx.isAdmitted();
                case "min_qty" -> ctx.qty().compareTo(new BigDecimal(expected.toString())) >= 0;
                case "max_qty" -> ctx.qty().compareTo(new BigDecimal(expected.toString())) <= 0;
                case "time_after" -> {
                    int hour = Integer.parseInt(expected.toString());
                    yield ctx.serviceTime() != null && ctx.serviceTime().getHour() >= hour;
                }
                case "time_before" -> {
                    int hour = Integer.parseInt(expected.toString());
                    yield ctx.serviceTime() != null && ctx.serviceTime().getHour() < hour;
                }
                default -> {
                    String extra = ctx.getExtra(key);
                    yield expected.toString().equals(extra);
                }
            };

            if (!matches) return false;
        }
        return true;
    }
}
