package zw.gov.mohcc.impilo.costa.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.AbcCostPoolEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.AbcDriverRateEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;
import zw.gov.mohcc.impilo.costa.domain.repository.AbcCostPoolRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.AbcDriverRateRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbcCostEngineTest {

    @Mock
    private AbcCostPoolRepository poolRepository;
    @Mock
    private AbcDriverRateRepository driverRateRepository;

    private AbcCostEngine engine;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new AbcCostEngine(poolRepository, driverRateRepository);
    }

    @Test
    void shouldReturnMethodTypeAbc() {
        assertEquals(CostMethodType.ABC, engine.getMethodType());
    }

    @Test
    void shouldAllocateOverheadFromSinglePool() {
        AbcCostPoolEntity pool = new AbcCostPoolEntity();
        pool.setId(1L);
        pool.setPoolName("Administration");
        pool.setAnnualCost(new BigDecimal("120000.00"));
        pool.setDriverType("encounters");
        pool.setEffectiveFrom(LocalDate.now().minusMonths(6));

        AbcDriverRateEntity rate = new AbcDriverRateEntity();
        rate.setPoolId(1L);
        rate.setDriverUnit("encounters");
        rate.setAnnualVolume(new BigDecimal("10000"));
        rate.setRate(new BigDecimal("12.000000")); // 120000/10000
        rate.setEffectiveFrom(LocalDate.now().minusMonths(6));

        when(poolRepository.findEffectivePools(eq(tenantId), eq(facilityId), any()))
                .thenReturn(List.of(pool));
        when(driverRateRepository.findEffectiveRates(eq(1L), any()))
                .thenReturn(List.of(rate));

        CostResult result = engine.compute(tenantId, facilityId, "TEST-001",
                BigDecimal.ONE, Map.of("driver_encounters", "1"));

        assertEquals(CostMethodType.ABC, result.methodUsed());
        assertEquals(new BigDecimal("12.00"), result.unitCost());
        assertTrue(result.trace().containsKey("pool_allocations"));
    }

    @Test
    void shouldReturnZeroWhenNoPoolsConfigured() {
        when(poolRepository.findEffectivePools(eq(tenantId), eq(facilityId), any()))
                .thenReturn(List.of());

        CostResult result = engine.compute(tenantId, facilityId, "TEST-001",
                BigDecimal.ONE, Map.of());

        assertEquals(BigDecimal.ZERO, result.totalCost());
    }

    @Test
    void shouldCombineMultiplePools() {
        AbcCostPoolEntity adminPool = new AbcCostPoolEntity();
        adminPool.setId(1L);
        adminPool.setPoolName("Administration");
        adminPool.setAnnualCost(new BigDecimal("100000"));
        adminPool.setDriverType("encounters");
        adminPool.setEffectiveFrom(LocalDate.now().minusMonths(6));

        AbcCostPoolEntity utilPool = new AbcCostPoolEntity();
        utilPool.setId(2L);
        utilPool.setPoolName("Utilities");
        utilPool.setAnnualCost(new BigDecimal("50000"));
        utilPool.setDriverType("bed_days");
        utilPool.setEffectiveFrom(LocalDate.now().minusMonths(6));

        AbcDriverRateEntity adminRate = new AbcDriverRateEntity();
        adminRate.setDriverUnit("encounters");
        adminRate.setAnnualVolume(new BigDecimal("10000"));
        adminRate.setRate(new BigDecimal("10.000000"));
        adminRate.setEffectiveFrom(LocalDate.now().minusMonths(6));

        AbcDriverRateEntity utilRate = new AbcDriverRateEntity();
        utilRate.setDriverUnit("bed_days");
        utilRate.setAnnualVolume(new BigDecimal("5000"));
        utilRate.setRate(new BigDecimal("10.000000"));
        utilRate.setEffectiveFrom(LocalDate.now().minusMonths(6));

        when(poolRepository.findEffectivePools(eq(tenantId), eq(facilityId), any()))
                .thenReturn(List.of(adminPool, utilPool));
        when(driverRateRepository.findEffectiveRates(eq(1L), any())).thenReturn(List.of(adminRate));
        when(driverRateRepository.findEffectiveRates(eq(2L), any())).thenReturn(List.of(utilRate));

        CostResult result = engine.compute(tenantId, facilityId, "TEST-001",
                BigDecimal.ONE, Map.of("driver_encounters", "1", "driver_bed_days", "3"));

        // admin: 10*1=10, util: 10*3=30, total=40
        assertEquals(new BigDecimal("40.00"), result.unitCost());
    }
}
