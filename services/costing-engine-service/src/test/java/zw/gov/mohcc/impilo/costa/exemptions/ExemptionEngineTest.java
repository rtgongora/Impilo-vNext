package zw.gov.mohcc.impilo.costa.exemptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.ExemptionRuleEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.InsurancePlanEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ExemptionCategory;
import zw.gov.mohcc.impilo.costa.domain.repository.ExemptionRuleRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InsurancePlanRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExemptionEngineTest {

    @Mock
    private ExemptionRuleRepository exemptionRuleRepository;
    @Mock
    private InsurancePlanRepository insurancePlanRepository;

    private ExemptionEngine engine;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new ExemptionEngine(exemptionRuleRepository, insurancePlanRepository, new ObjectMapper());
    }

    @Test
    void shouldSplitFullyToPatientWhenNoExemptionOrInsurance() {
        BillSplitResult result = engine.split(tenantId, new BigDecimal("500.00"),
                null, null, Map.of());

        assertEquals(new BigDecimal("500.00"), result.patientPayable());
        assertEquals(BigDecimal.ZERO, result.insurerPayable());
        assertEquals(BigDecimal.ZERO, result.subsidyPayable());
    }

    @Test
    void shouldApplyFullExemptionAsWriteOff() {
        ExemptionRuleEntity exemption = new ExemptionRuleEntity();
        exemption.setId(1L);
        exemption.setCategory(ExemptionCategory.CHILD);
        exemption.setDiscountPct(new BigDecimal("100.00"));
        exemption.setEffectiveFrom(LocalDate.now().minusDays(30));

        when(exemptionRuleRepository.findEffective(eq(tenantId), eq(ExemptionCategory.CHILD), any()))
                .thenReturn(List.of(exemption));

        BillSplitResult result = engine.split(tenantId, new BigDecimal("500.00"),
                "CHILD", null, Map.of());

        assertEquals(BigDecimal.ZERO, result.patientPayable().stripTrailingZeros());
        assertEquals(new BigDecimal("500.00"), result.writeOff());
    }

    @Test
    void shouldApplyPartialExemptionAsSubsidy() {
        ExemptionRuleEntity exemption = new ExemptionRuleEntity();
        exemption.setId(1L);
        exemption.setCategory(ExemptionCategory.ELDERLY);
        exemption.setDiscountPct(new BigDecimal("50.00"));
        exemption.setEffectiveFrom(LocalDate.now().minusDays(30));

        when(exemptionRuleRepository.findEffective(eq(tenantId), eq(ExemptionCategory.ELDERLY), any()))
                .thenReturn(List.of(exemption));

        BillSplitResult result = engine.split(tenantId, new BigDecimal("400.00"),
                "ELDERLY", null, Map.of());

        assertEquals(new BigDecimal("200.00"), result.patientPayable());
        assertEquals(new BigDecimal("200.00"), result.subsidyPayable());
    }

    @Test
    void shouldApplyInsuranceCoverage() {
        InsurancePlanEntity plan = new InsurancePlanEntity();
        plan.setId(1L);
        plan.setPlanCode("GOLD");
        plan.setInsurerName("NatHealth");
        plan.setCoveragePct(new BigDecimal("80.00"));
        plan.setDeductible(BigDecimal.ZERO);
        plan.setCopayPct(new BigDecimal("20.00"));
        plan.setEffectiveFrom(LocalDate.now().minusDays(30));

        when(insurancePlanRepository.findById(1L)).thenReturn(Optional.of(plan));

        BillSplitResult result = engine.split(tenantId, new BigDecimal("1000.00"),
                null, 1L, Map.of());

        // Coverage 80%, copay 20%: insurer = 80%-20% = 60%, patient = 40%
        assertEquals(new BigDecimal("600.00"), result.insurerPayable());
        assertEquals(new BigDecimal("400.00"), result.patientPayable());
    }

    @Test
    void shouldApplyExemptionThenInsurance() {
        ExemptionRuleEntity exemption = new ExemptionRuleEntity();
        exemption.setId(1L);
        exemption.setCategory(ExemptionCategory.MATERNITY);
        exemption.setDiscountPct(new BigDecimal("25.00"));
        exemption.setEffectiveFrom(LocalDate.now().minusDays(30));

        InsurancePlanEntity plan = new InsurancePlanEntity();
        plan.setId(2L);
        plan.setPlanCode("SILVER");
        plan.setInsurerName("MedAid");
        plan.setCoveragePct(new BigDecimal("80.00"));
        plan.setDeductible(BigDecimal.ZERO);
        plan.setCopayPct(new BigDecimal("20.00"));
        plan.setEffectiveFrom(LocalDate.now().minusDays(30));

        when(exemptionRuleRepository.findEffective(eq(tenantId), eq(ExemptionCategory.MATERNITY), any()))
                .thenReturn(List.of(exemption));
        when(insurancePlanRepository.findById(2L)).thenReturn(Optional.of(plan));

        BillSplitResult result = engine.split(tenantId, new BigDecimal("1000.00"),
                "MATERNITY", 2L, Map.of());

        // Exemption: 25% of 1000 = 250 (subsidy), remaining = 750
        // Insurance: 80% of 750 = 600, copay 20% of 750 = 150, insurer = 600-150=450
        // Patient: 750-450 = 300
        assertEquals(new BigDecimal("250.00"), result.subsidyPayable());
        assertEquals(new BigDecimal("450.00"), result.insurerPayable());
        assertEquals(new BigDecimal("300.00"), result.patientPayable());
    }

    @Test
    void shouldApplyExemptionCapAmount() {
        ExemptionRuleEntity exemption = new ExemptionRuleEntity();
        exemption.setId(1L);
        exemption.setCategory(ExemptionCategory.CHRONIC);
        exemption.setDiscountPct(new BigDecimal("50.00"));
        exemption.setMaxAmount(new BigDecimal("100.00")); // Cap at 100
        exemption.setEffectiveFrom(LocalDate.now().minusDays(30));

        when(exemptionRuleRepository.findEffective(eq(tenantId), eq(ExemptionCategory.CHRONIC), any()))
                .thenReturn(List.of(exemption));

        BillSplitResult result = engine.split(tenantId, new BigDecimal("500.00"),
                "CHRONIC", null, Map.of());

        // 50% of 500 = 250, but cap = 100
        assertEquals(new BigDecimal("100.00"), result.subsidyPayable());
        assertEquals(new BigDecimal("400.00"), result.patientPayable());
    }
}
