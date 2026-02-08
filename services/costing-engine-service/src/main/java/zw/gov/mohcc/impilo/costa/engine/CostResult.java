package zw.gov.mohcc.impilo.costa.engine;

import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable result of a cost computation. Every computed line must include
 * cost_method_used, inputs, unit cost source, and optional overhead allocation trace.
 */
public record CostResult(
        BigDecimal unitCost,
        BigDecimal totalCost,
        BigDecimal qty,
        CostMethodType methodUsed,
        String unitCostSource,
        Map<String, Object> trace
) {
    public static CostResult of(BigDecimal unitCost, BigDecimal qty, CostMethodType method,
                                String source, Map<String, Object> trace) {
        BigDecimal total = unitCost.multiply(qty);
        return new CostResult(unitCost, total, qty, method, source, trace);
    }

    public static CostResult zero(CostMethodType method, String reason) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("reason", reason);
        return new CostResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, method, "NONE", trace);
    }
}
