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
 * Weighted average cost engine for stock items.
 * Uses the moving-average cost per item per store/bin from inventory.
 * Falls back to INVENTORY-sourced unit cost records.
 */
@Component
public class StockAverageCostEngine implements CostEngine {

    private static final Logger log = LoggerFactory.getLogger(StockAverageCostEngine.class);

    private final UnitCostSourceRepository unitCostSourceRepository;

    public StockAverageCostEngine(UnitCostSourceRepository unitCostSourceRepository) {
        this.unitCostSourceRepository = unitCostSourceRepository;
    }

    @Override
    public CostMethodType getMethodType() {
        return CostMethodType.STOCK_AVG;
    }

    @Override
    public CostResult compute(UUID tenantId, UUID facilityId, String msikaCode,
                              BigDecimal qty, Map<String, Object> context) {
        LocalDate asOf = LocalDate.now();

        // If the context provides a moving average cost directly (from inventory service event)
        if (context.containsKey("weighted_avg_cost")) {
            BigDecimal wac = new BigDecimal(context.get("weighted_avg_cost").toString());
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("cost_method", "STOCK_AVG");
            trace.put("msika_code", msikaCode);
            trace.put("weighted_avg_cost", wac);
            trace.put("source", "EVENT_CONTEXT");
            trace.put("qty", qty);
            trace.put("total", wac.multiply(qty));

            if (context.containsKey("batch_number")) {
                trace.put("batch_number", context.get("batch_number"));
            }
            if (context.containsKey("store_id")) {
                trace.put("store_id", context.get("store_id"));
            }

            return CostResult.of(wac, qty, CostMethodType.STOCK_AVG, "EVENT_CONTEXT", trace);
        }

        // Fall back to persisted unit cost sources of type INVENTORY
        List<UnitCostSourceEntity> sources = unitCostSourceRepository.findEffective(tenantId, msikaCode, asOf);
        UnitCostSourceEntity inventorySource = sources.stream()
                .filter(s -> s.getSourceType() == UnitCostSourceType.INVENTORY)
                .findFirst()
                .orElse(null);

        if (inventorySource == null) {
            return CostResult.zero(CostMethodType.STOCK_AVG,
                    "No inventory cost source for: " + msikaCode);
        }

        BigDecimal unitCost = inventorySource.getUnitCost();

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("cost_method", "STOCK_AVG");
        trace.put("msika_code", msikaCode);
        trace.put("weighted_avg_cost", unitCost);
        trace.put("source_id", inventorySource.getId());
        trace.put("source_type", "INVENTORY");
        trace.put("qty", qty);
        trace.put("total", unitCost.multiply(qty));

        return CostResult.of(unitCost, qty, CostMethodType.STOCK_AVG,
                "INVENTORY:" + inventorySource.getId(), trace);
    }
}
