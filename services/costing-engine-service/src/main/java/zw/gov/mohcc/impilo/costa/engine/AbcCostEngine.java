package zw.gov.mohcc.impilo.costa.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.costa.domain.entity.AbcCostPoolEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.AbcDriverRateEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;
import zw.gov.mohcc.impilo.costa.domain.repository.AbcCostPoolRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.AbcDriverRateRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Activity-Based Costing (ABC) engine.
 * Allocates overhead costs using activity drivers (minutes, encounters, tests).
 * Rate = pool annual cost / annual driver volume.
 * Item cost = sum of (driver_qty * rate) across all applicable pools.
 */
@Component
public class AbcCostEngine implements CostEngine {

    private static final Logger log = LoggerFactory.getLogger(AbcCostEngine.class);

    private final AbcCostPoolRepository poolRepository;
    private final AbcDriverRateRepository driverRateRepository;

    public AbcCostEngine(AbcCostPoolRepository poolRepository,
                         AbcDriverRateRepository driverRateRepository) {
        this.poolRepository = poolRepository;
        this.driverRateRepository = driverRateRepository;
    }

    @Override
    public CostMethodType getMethodType() {
        return CostMethodType.ABC;
    }

    @Override
    public CostResult compute(UUID tenantId, UUID facilityId, String msikaCode,
                              BigDecimal qty, Map<String, Object> context) {
        LocalDate asOf = LocalDate.now();

        List<AbcCostPoolEntity> pools = poolRepository.findEffectivePools(tenantId, facilityId, asOf);

        if (pools.isEmpty()) {
            return CostResult.zero(CostMethodType.ABC, "No ABC cost pools configured for facility");
        }

        BigDecimal totalOverhead = BigDecimal.ZERO;
        List<Map<String, Object>> poolTraces = new ArrayList<>();

        for (AbcCostPoolEntity pool : pools) {
            List<AbcDriverRateEntity> rates = driverRateRepository.findEffectiveRates(pool.getId(), asOf);
            if (rates.isEmpty()) continue;

            AbcDriverRateEntity rate = rates.get(0);
            String driverUnit = rate.getDriverUnit();

            // Get the driver quantity from context, default to 1
            BigDecimal driverQty = BigDecimal.ONE;
            if (context.containsKey("driver_" + driverUnit)) {
                driverQty = new BigDecimal(context.get("driver_" + driverUnit).toString());
            } else if (context.containsKey("driver_qty")) {
                driverQty = new BigDecimal(context.get("driver_qty").toString());
            }

            BigDecimal allocation = rate.getRate().multiply(driverQty);
            totalOverhead = totalOverhead.add(allocation);

            Map<String, Object> poolTrace = new LinkedHashMap<>();
            poolTrace.put("pool_name", pool.getPoolName());
            poolTrace.put("pool_id", pool.getId());
            poolTrace.put("annual_cost", pool.getAnnualCost());
            poolTrace.put("driver_type", pool.getDriverType());
            poolTrace.put("driver_unit", driverUnit);
            poolTrace.put("driver_volume", rate.getAnnualVolume());
            poolTrace.put("rate_per_unit", rate.getRate());
            poolTrace.put("driver_qty_used", driverQty);
            poolTrace.put("allocation", allocation);
            poolTraces.add(poolTrace);
        }

        BigDecimal unitOverhead = qty.compareTo(BigDecimal.ZERO) > 0
                ? totalOverhead.divide(qty, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("cost_method", "ABC");
        trace.put("msika_code", msikaCode);
        trace.put("pools_evaluated", pools.size());
        trace.put("total_overhead_allocated", totalOverhead);
        trace.put("unit_overhead", unitOverhead);
        trace.put("qty", qty);
        trace.put("pool_allocations", poolTraces);

        return new CostResult(unitOverhead, totalOverhead, qty,
                CostMethodType.ABC, "ABC_POOL_ALLOCATION", trace);
    }
}
