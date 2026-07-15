package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.RiskRuleEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.RiskRuleRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Deterministic, pure, table-driven wellness risk scoring. Reads {@code simba_risk_rule} config and
 * evaluates assessment responses into a band. This is NOT a clinical diagnosis — a HIGH/URGENT band
 * routes to PCT via {@link CareLinkageService}; Simba never owns the clinical decision.
 *
 * <p>Bands (ascending severity): LOW, MODERATE, HIGH, NEEDS_CLINICAL_REVIEW, URGENT_RED_FLAG.
 * Any matched {@code red_flag} rule short-circuits to URGENT_RED_FLAG. Otherwise the resulting band
 * is the highest-severity band any matched rule contributes to. Per-rule contributions are returned
 * for transparency (stored in the assessment's {@code risk_detail}).
 */
@Service
public class RiskScoringService {

    private final RiskRuleRepository ruleRepository;

    public RiskScoringService(RiskRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    /** Band severity ordering (index = severity rank). */
    private static final List<String> BAND_ORDER = List.of(
            "LOW", "MODERATE", "HIGH", "NEEDS_CLINICAL_REVIEW", "URGENT_RED_FLAG");

    public record RuleContribution(String ruleCode, String questionCode, String band,
                                   double weight, boolean redFlag, String rationale) { }

    public record RiskResult(String band, double score, List<RuleContribution> contributions) { }

    /** Evaluates the person's responses against the template's active rules. */
    @Transactional(readOnly = true)
    public RiskResult score(UUID tenantId, String templateCode, Map<String, Object> responses) {
        List<RiskRuleEntity> rules =
                ruleRepository.findByTenantIdAndTemplateCodeAndStatus(tenantId, templateCode, "ACTIVE");
        return evaluate(rules, responses);
    }

    /** Pure evaluation — extracted for table-driven unit testing without a datasource. */
    public static RiskResult evaluate(List<RiskRuleEntity> rules, Map<String, Object> responses) {
        List<RuleContribution> hits = new ArrayList<>();
        int highestBandIdx = 0; // LOW
        double score = 0.0;
        boolean redFlagged = false;

        for (RiskRuleEntity rule : rules) {
            Object raw = responses.get(rule.getQuestionCode());
            if (raw == null) {
                continue;
            }
            if (!matches(rule, raw)) {
                continue;
            }
            double weight = rule.getWeight() == null ? 1.0 : rule.getWeight().doubleValue();
            hits.add(new RuleContribution(rule.getRuleCode(), rule.getQuestionCode(),
                    rule.getContributesBand(), weight, rule.isRedFlag(), rule.getRationale()));
            score += weight;
            int idx = BAND_ORDER.indexOf(rule.getContributesBand());
            if (idx > highestBandIdx) {
                highestBandIdx = idx;
            }
            if (rule.isRedFlag()) {
                redFlagged = true;
            }
        }

        String band = redFlagged ? "URGENT_RED_FLAG" : BAND_ORDER.get(highestBandIdx);
        return new RiskResult(band, score, hits);
    }

    private static boolean matches(RiskRuleEntity rule, Object raw) {
        String op = rule.getOperator() == null ? "" : rule.getOperator().toUpperCase();
        switch (op) {
            case "EQ" -> {
                // Numeric EQ if both parse; else text EQ against match_values.
                Double n = asNumber(raw);
                if (n != null && rule.getThresholdLow() != null) {
                    return n == rule.getThresholdLow().doubleValue();
                }
                return matchesTextSet(rule.getMatchValues(), asText(raw));
            }
            case "IN" -> {
                return matchesTextSet(rule.getMatchValues(), asText(raw));
            }
            case "GT", "GTE", "LT", "LTE", "RANGE" -> {
                Double n = asNumber(raw);
                if (n == null) {
                    return false;
                }
                Double low = rule.getThresholdLow() == null ? null : rule.getThresholdLow().doubleValue();
                Double high = rule.getThresholdHigh() == null ? null : rule.getThresholdHigh().doubleValue();
                return switch (op) {
                    case "GT" -> low != null && n > low;
                    case "GTE" -> low != null && n >= low;
                    case "LT" -> low != null && n < low;
                    case "LTE" -> low != null && n <= low;
                    case "RANGE" -> low != null && high != null && n >= low && n <= high;
                    default -> false;
                };
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean matchesTextSet(String matchValues, String value) {
        if (matchValues == null || value == null) {
            return false;
        }
        Set<String> set = Arrays.stream(matchValues.split(","))
                .map(String::trim).map(String::toUpperCase).collect(Collectors.toSet());
        return set.contains(value.trim().toUpperCase());
    }

    private static Double asNumber(Object raw) {
        if (raw instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return new BigDecimal(String.valueOf(raw).trim()).doubleValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String asText(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    /** Bands at/above HIGH warrant a care linkage handoff. */
    public static boolean warrantsCareLinkage(String band) {
        int idx = BAND_ORDER.indexOf(band);
        return idx >= BAND_ORDER.indexOf("HIGH");
    }

    /** True for the most urgent band (self-harm / hypertensive crisis red flags). */
    public static boolean isUrgent(String band) {
        return "URGENT_RED_FLAG".equals(band);
    }

    /** Detail map suitable for the assessment risk_detail JSONB / timeline. */
    public static Map<String, Object> detailOf(RiskResult result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("band", result.band());
        detail.put("score", result.score());
        List<Map<String, Object>> contribs = new ArrayList<>();
        for (RuleContribution c : result.contributions()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rule_code", c.ruleCode());
            m.put("question_code", c.questionCode());
            m.put("band", c.band());
            m.put("weight", c.weight());
            m.put("red_flag", c.redFlag());
            m.put("rationale", c.rationale());
            contribs.add(m);
        }
        detail.put("contributions", contribs);
        return detail;
    }
}
