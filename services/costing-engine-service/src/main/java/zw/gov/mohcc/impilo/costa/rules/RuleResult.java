package zw.gov.mohcc.impilo.costa.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Result of charging rule evaluation for a single item.
 */
public record RuleResult(
        BigDecimal chargeAmount,
        BigDecimal markupPct,
        BigDecimal discountPct,
        boolean bundled,
        boolean excluded,
        List<String> appliedRules,
        Map<String, Object> trace
) {
    public static RuleResult passThrough(BigDecimal amount) {
        return new RuleResult(amount, BigDecimal.ZERO, BigDecimal.ZERO,
                false, false, List.of("DEFAULT_PASSTHROUGH"),
                Map.of("rule", "passthrough", "amount", amount));
    }
}
