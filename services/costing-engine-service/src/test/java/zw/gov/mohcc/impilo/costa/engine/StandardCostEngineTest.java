package zw.gov.mohcc.impilo.costa.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.UnitCostSourceEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;
import zw.gov.mohcc.impilo.costa.domain.enums.UnitCostSourceType;
import zw.gov.mohcc.impilo.costa.domain.repository.UnitCostSourceRepository;

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
class StandardCostEngineTest {

    @Mock
    private UnitCostSourceRepository unitCostSourceRepository;

    private StandardCostEngine engine;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new StandardCostEngine(unitCostSourceRepository);
    }

    @Test
    void shouldComputeStandardCost() {
        UnitCostSourceEntity source = new UnitCostSourceEntity();
        source.setId(1L);
        source.setUnitCost(new BigDecimal("20.00"));
        source.setSourceType(UnitCostSourceType.MANUAL);
        source.setEffectiveFrom(LocalDate.now().minusDays(30));

        when(unitCostSourceRepository.findEffective(eq(tenantId), eq("PROC-001"), any()))
                .thenReturn(List.of(source));

        CostResult result = engine.compute(tenantId, facilityId, "PROC-001",
                new BigDecimal("3"), Map.of());

        assertEquals(new BigDecimal("20.00"), result.unitCost());
        assertEquals(new BigDecimal("60.00"), result.totalCost());
    }

    @Test
    void shouldCaptureVariance() {
        UnitCostSourceEntity source = new UnitCostSourceEntity();
        source.setId(1L);
        source.setUnitCost(new BigDecimal("20.00"));
        source.setSourceType(UnitCostSourceType.MANUAL);
        source.setEffectiveFrom(LocalDate.now().minusDays(30));

        when(unitCostSourceRepository.findEffective(eq(tenantId), eq("PROC-001"), any()))
                .thenReturn(List.of(source));

        CostResult result = engine.compute(tenantId, facilityId, "PROC-001",
                BigDecimal.ONE, Map.of("actual_unit_cost", "25.00"));

        assertEquals(new BigDecimal("20.00"), result.unitCost());
        assertEquals(new BigDecimal("5.00"), result.trace().get("variance"));
        assertEquals("UNFAVORABLE", result.trace().get("variance_type"));
    }

    @Test
    void shouldDetectFavorableVariance() {
        UnitCostSourceEntity source = new UnitCostSourceEntity();
        source.setId(1L);
        source.setUnitCost(new BigDecimal("20.00"));
        source.setSourceType(UnitCostSourceType.MANUAL);
        source.setEffectiveFrom(LocalDate.now().minusDays(30));

        when(unitCostSourceRepository.findEffective(eq(tenantId), eq("PROC-001"), any()))
                .thenReturn(List.of(source));

        CostResult result = engine.compute(tenantId, facilityId, "PROC-001",
                BigDecimal.ONE, Map.of("actual_unit_cost", "18.00"));

        assertEquals("FAVORABLE", result.trace().get("variance_type"));
    }
}
