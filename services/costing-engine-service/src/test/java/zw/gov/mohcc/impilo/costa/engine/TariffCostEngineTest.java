package zw.gov.mohcc.impilo.costa.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.TariffEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;
import zw.gov.mohcc.impilo.costa.domain.repository.TariffRepository;

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
class TariffCostEngineTest {

    @Mock
    private TariffRepository tariffRepository;

    private TariffCostEngine engine;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new TariffCostEngine(tariffRepository);
    }

    @Test
    void shouldReturnMethodTypeTariff() {
        assertEquals(CostMethodType.TARIFF, engine.getMethodType());
    }

    @Test
    void shouldLookUpTariffByMsikaCode() {
        TariffEntity tariff = new TariffEntity();
        tariff.setId(1L);
        tariff.setTariffCode("CONSULT-GP");
        tariff.setMsikaCode("CONSULT-GP");
        tariff.setDescription("General practitioner consultation");
        tariff.setPrice(new BigDecimal("15.00"));
        tariff.setCurrency("USD");
        tariff.setEffectiveFrom(LocalDate.now().minusDays(90));

        when(tariffRepository.findByMsikaCode(eq(tenantId), eq("CONSULT-GP"), any()))
                .thenReturn(List.of(tariff));

        CostResult result = engine.compute(tenantId, facilityId, "CONSULT-GP",
                BigDecimal.ONE, Map.of());

        assertEquals(new BigDecimal("15.00"), result.unitCost());
        assertEquals(new BigDecimal("15.00"), result.totalCost());
        assertEquals("TARIFF:CONSULT-GP", result.unitCostSource());
    }

    @Test
    void shouldSelectMostSpecificTariff() {
        TariffEntity generic = new TariffEntity();
        generic.setId(1L);
        generic.setTariffCode("BEDDAY");
        generic.setPrice(new BigDecimal("50.00"));
        generic.setEffectiveFrom(LocalDate.now().minusDays(90));

        TariffEntity facilitySpecific = new TariffEntity();
        facilitySpecific.setId(2L);
        facilitySpecific.setTariffCode("BEDDAY");
        facilitySpecific.setPrice(new BigDecimal("75.00"));
        facilitySpecific.setFacilityCategory("CENTRAL");
        facilitySpecific.setEffectiveFrom(LocalDate.now().minusDays(90));

        TariffEntity mostSpecific = new TariffEntity();
        mostSpecific.setId(3L);
        mostSpecific.setTariffCode("BEDDAY");
        mostSpecific.setPrice(new BigDecimal("100.00"));
        mostSpecific.setFacilityCategory("CENTRAL");
        mostSpecific.setPatientCategory("PRIVATE");
        mostSpecific.setEffectiveFrom(LocalDate.now().minusDays(90));

        when(tariffRepository.findByMsikaCode(eq(tenantId), eq("BEDDAY"), any()))
                .thenReturn(List.of(generic, facilitySpecific, mostSpecific));

        CostResult result = engine.compute(tenantId, facilityId, "BEDDAY",
                new BigDecimal("5"), Map.of("facility_category", "CENTRAL", "patient_category", "PRIVATE"));

        // Should select the most specific match (CENTRAL + PRIVATE)
        assertEquals(new BigDecimal("100.00"), result.unitCost());
        assertEquals(new BigDecimal("500.00"), result.totalCost());
    }

    @Test
    void shouldReturnZeroWhenNoTariffFound() {
        when(tariffRepository.findByMsikaCode(eq(tenantId), eq("UNKNOWN"), any())).thenReturn(List.of());
        when(tariffRepository.findEffectiveTariff(eq(tenantId), eq("UNKNOWN"), any())).thenReturn(List.of());

        CostResult result = engine.compute(tenantId, facilityId, "UNKNOWN",
                BigDecimal.ONE, Map.of());

        assertEquals(BigDecimal.ZERO, result.totalCost());
    }
}
