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
class MicroCostEngineTest {

    @Mock
    private UnitCostSourceRepository unitCostSourceRepository;

    private MicroCostEngine engine;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new MicroCostEngine(unitCostSourceRepository);
    }

    @Test
    void shouldReturnMethodTypeMicro() {
        assertEquals(CostMethodType.MICRO, engine.getMethodType());
    }

    @Test
    void shouldComputeCostFromUnitCostSource() {
        UnitCostSourceEntity source = new UnitCostSourceEntity();
        source.setId(1L);
        source.setUnitCost(new BigDecimal("25.00"));
        source.setSourceType(UnitCostSourceType.MANUAL);
        source.setCurrency("USD");
        source.setEffectiveFrom(LocalDate.now().minusDays(30));

        when(unitCostSourceRepository.findEffective(eq(tenantId), eq("CONSULT-001"), any()))
                .thenReturn(List.of(source));

        CostResult result = engine.compute(tenantId, facilityId, "CONSULT-001",
                new BigDecimal("2"), Map.of());

        assertEquals(CostMethodType.MICRO, result.methodUsed());
        assertEquals(new BigDecimal("25.00"), result.unitCost());
        assertEquals(new BigDecimal("50.00"), result.totalCost());
        assertNotNull(result.trace());
        assertEquals("MANUAL", result.trace().get("source_type"));
    }

    @Test
    void shouldReturnZeroWhenNoSourceFound() {
        when(unitCostSourceRepository.findEffective(eq(tenantId), eq("UNKNOWN"), any()))
                .thenReturn(List.of());

        CostResult result = engine.compute(tenantId, facilityId, "UNKNOWN",
                BigDecimal.ONE, Map.of());

        assertEquals(BigDecimal.ZERO, result.totalCost());
        assertEquals(BigDecimal.ZERO, result.unitCost());
        assertEquals("No unit cost source configured", result.trace().get("reason"));
    }

    @Test
    void shouldHandleComponentBreakdown() {
        UnitCostSourceEntity mainSource = new UnitCostSourceEntity();
        mainSource.setId(1L);
        mainSource.setUnitCost(new BigDecimal("10.00"));
        mainSource.setSourceType(UnitCostSourceType.MANUAL);
        mainSource.setCurrency("USD");
        mainSource.setEffectiveFrom(LocalDate.now().minusDays(30));

        UnitCostSourceEntity compSource = new UnitCostSourceEntity();
        compSource.setId(2L);
        compSource.setUnitCost(new BigDecimal("5.00"));
        compSource.setSourceType(UnitCostSourceType.MANUAL);
        compSource.setCurrency("USD");
        compSource.setEffectiveFrom(LocalDate.now().minusDays(30));

        when(unitCostSourceRepository.findEffective(eq(tenantId), eq("PROCEDURE-001"), any()))
                .thenReturn(List.of(mainSource));
        when(unitCostSourceRepository.findEffective(eq(tenantId), eq("SUPPLY-A"), any()))
                .thenReturn(List.of(compSource));
        when(unitCostSourceRepository.findEffective(eq(tenantId), eq("SUPPLY-B"), any()))
                .thenReturn(List.of(compSource));

        List<Map<String, Object>> components = List.of(
                Map.of("code", "SUPPLY-A", "qty", "3"),
                Map.of("code", "SUPPLY-B", "qty", "2")
        );
        Map<String, Object> context = Map.of("components", components);

        CostResult result = engine.compute(tenantId, facilityId, "PROCEDURE-001",
                BigDecimal.ONE, context);

        // 3*5 + 2*5 = 25
        assertEquals(new BigDecimal("25.00"), result.totalCost());
        assertEquals("COMPONENT_BREAKDOWN", result.unitCostSource());
    }
}
