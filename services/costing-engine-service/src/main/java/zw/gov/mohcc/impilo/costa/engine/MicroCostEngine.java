package zw.gov.mohcc.impilo.costa.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.costa.domain.entity.UnitCostSourceEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;
import zw.gov.mohcc.impilo.costa.domain.repository.UnitCostSourceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Bottom-up micro-costing engine.
 * Computes cost as: resource_units * unit_cost for each resource component.
 * Supports staff time, bed-days, theatre minutes, consumables, tests.
 */
@Component
public class MicroCostEngine implements CostEngine {

    private static final Logger log = LoggerFactory.getLogger(MicroCostEngine.class);

    private final UnitCostSourceRepository unitCostSourceRepository;

    public MicroCostEngine(UnitCostSourceRepository unitCostSourceRepository) {
        this.unitCostSourceRepository = unitCostSourceRepository;
    }

    @Override
    public CostMethodType getMethodType() {
        return CostMethodType.MICRO;
    }

    @Override
    public CostResult compute(UUID tenantId, UUID facilityId, String msikaCode,
                              BigDecimal qty, Map<String, Object> context) {
        LocalDate asOf = LocalDate.now();

        List<UnitCostSourceEntity> sources = unitCostSourceRepository
                .findEffective(tenantId, msikaCode, asOf);

        if (sources.isEmpty()) {
            log.warn("No unit cost source found for tenant={}, msikaCode={}", tenantId, msikaCode);
            return CostResult.zero(CostMethodType.MICRO, "No unit cost source configured");
        }

        UnitCostSourceEntity source = sources.get(0);
        BigDecimal unitCost = source.getUnitCost();

        // Build trace showing each input used
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("cost_method", "MICRO");
        trace.put("msika_code", msikaCode);
        trace.put("unit_cost", unitCost);
        trace.put("unit_cost_source_id", source.getId());
        trace.put("source_type", source.getSourceType().name());
        trace.put("qty", qty);
        trace.put("total", unitCost.multiply(qty));
        trace.put("currency", source.getCurrency());
        trace.put("effective_from", source.getEffectiveFrom().toString());

        // If context has component breakdown, include it
        if (context.containsKey("components")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> components = (List<Map<String, Object>>) context.get("components");
            BigDecimal componentTotal = BigDecimal.ZERO;
            List<Map<String, Object>> componentTraces = new ArrayList<>();

            for (Map<String, Object> component : components) {
                String compCode = (String) component.get("code");
                BigDecimal compQty = new BigDecimal(component.get("qty").toString());
                BigDecimal compUnitCost = resolveComponentCost(tenantId, compCode, asOf);
                BigDecimal compTotal = compUnitCost.multiply(compQty);
                componentTotal = componentTotal.add(compTotal);

                Map<String, Object> compTrace = new LinkedHashMap<>();
                compTrace.put("code", compCode);
                compTrace.put("qty", compQty);
                compTrace.put("unit_cost", compUnitCost);
                compTrace.put("total", compTotal);
                componentTraces.add(compTrace);
            }

            trace.put("components", componentTraces);
            trace.put("component_total", componentTotal);
            return new CostResult(componentTotal.divide(qty, 2, java.math.RoundingMode.HALF_UP),
                    componentTotal, qty, CostMethodType.MICRO, "COMPONENT_BREAKDOWN", trace);
        }

        return CostResult.of(unitCost, qty, CostMethodType.MICRO,
                source.getSourceType().name(), trace);
    }

    private BigDecimal resolveComponentCost(UUID tenantId, String code, LocalDate asOf) {
        List<UnitCostSourceEntity> sources = unitCostSourceRepository.findEffective(tenantId, code, asOf);
        if (sources.isEmpty()) return BigDecimal.ZERO;
        return sources.get(0).getUnitCost();
    }
}
