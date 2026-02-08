package zw.gov.mohcc.impilo.costa.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.costa.domain.entity.UnitCostSourceEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;
import zw.gov.mohcc.impilo.costa.domain.enums.UnitCostSourceType;
import zw.gov.mohcc.impilo.costa.domain.repository.UnitCostSourceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Standard costing engine.
 * Uses predefined standard costs per service/item.
 * Captures variance between standard cost and actual cost if actual is provided in context.
 */
@Component
public class StandardCostEngine implements CostEngine {

    private static final Logger log = LoggerFactory.getLogger(StandardCostEngine.class);

    private final UnitCostSourceRepository unitCostSourceRepository;

    public StandardCostEngine(UnitCostSourceRepository unitCostSourceRepository) {
        this.unitCostSourceRepository = unitCostSourceRepository;
    }

    @Override
    public CostMethodType getMethodType() {
        return CostMethodType.STANDARD;
    }

    @Override
    public CostResult compute(UUID tenantId, UUID facilityId, String msikaCode,
                              BigDecimal qty, Map<String, Object> context) {
        LocalDate asOf = LocalDate.now();

        // Find the standard cost source (type=MANUAL, i.e., predefined)
        List<UnitCostSourceEntity> sources = unitCostSourceRepository.findEffective(tenantId, msikaCode, asOf);

        // Prefer MANUAL (standard) sources
        UnitCostSourceEntity standardSource = sources.stream()
                .filter(s -> s.getSourceType() == UnitCostSourceType.MANUAL)
                .findFirst()
                .orElse(sources.isEmpty() ? null : sources.get(0));

        if (standardSource == null) {
            return CostResult.zero(CostMethodType.STANDARD, "No standard cost defined for: " + msikaCode);
        }

        BigDecimal standardUnitCost = standardSource.getUnitCost();
        BigDecimal totalCost = standardUnitCost.multiply(qty);

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("cost_method", "STANDARD");
        trace.put("msika_code", msikaCode);
        trace.put("standard_unit_cost", standardUnitCost);
        trace.put("source_id", standardSource.getId());
        trace.put("source_type", standardSource.getSourceType().name());
        trace.put("qty", qty);
        trace.put("total", totalCost);

        // Capture variance if actual cost is provided
        if (context.containsKey("actual_unit_cost")) {
            BigDecimal actualUnitCost = new BigDecimal(context.get("actual_unit_cost").toString());
            BigDecimal variance = actualUnitCost.subtract(standardUnitCost);
            BigDecimal variancePct = standardUnitCost.compareTo(BigDecimal.ZERO) > 0
                    ? variance.divide(standardUnitCost, 4, java.math.RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO;

            trace.put("actual_unit_cost", actualUnitCost);
            trace.put("variance", variance);
            trace.put("variance_pct", variancePct);
            trace.put("variance_type", variance.compareTo(BigDecimal.ZERO) > 0 ? "UNFAVORABLE" : "FAVORABLE");
        }

        return CostResult.of(standardUnitCost, qty, CostMethodType.STANDARD,
                "STANDARD:" + standardSource.getId(), trace);
    }
}
