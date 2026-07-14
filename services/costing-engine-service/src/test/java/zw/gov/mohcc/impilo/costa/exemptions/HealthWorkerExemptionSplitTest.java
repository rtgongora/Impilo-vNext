package zw.gov.mohcc.impilo.costa.exemptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.ExemptionRuleEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ExemptionCategory;
import zw.gov.mohcc.impilo.costa.domain.repository.ExemptionRuleRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InsurancePlanRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * HEALTH_WORKER split math through the (unchanged) ExemptionEngine:
 * percentage discount, cap enforcement, and the governed-disable posture —
 * a DISABLED rule is invisible to findEffective, so no discount applies.
 */
@ExtendWith(MockitoExtension.class)
class HealthWorkerExemptionSplitTest {

    @Mock private ExemptionRuleRepository exemptionRuleRepository;
    @Mock private InsurancePlanRepository insurancePlanRepository;

    private ExemptionEngine engine;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new ExemptionEngine(exemptionRuleRepository, insurancePlanRepository, new ObjectMapper());
    }

    private ExemptionRuleEntity rule(String pct, String cap) {
        ExemptionRuleEntity r = new ExemptionRuleEntity();
        r.setId(99L);
        r.setTenantId(tenantId);
        r.setName("Recognised health worker fee discount");
        r.setCategory(ExemptionCategory.HEALTH_WORKER);
        r.setDiscountPct(new BigDecimal(pct));
        if (cap != null) {
            r.setMaxAmount(new BigDecimal(cap));
        }
        r.setEffectiveFrom(LocalDate.now().minusDays(1));
        r.setStatus("ACTIVE");
        return r;
    }

    @Test
    void percentageDiscountReducesPatientShare() {
        when(exemptionRuleRepository.findEffective(eq(tenantId), eq(ExemptionCategory.HEALTH_WORKER), any()))
                .thenReturn(List.of(rule("50.00", "200.00")));

        BillSplitResult result = engine.split(
                tenantId, new BigDecimal("100.00"), "HEALTH_WORKER", null, Map.of());

        // 50% of 100.00 = 50.00 discount; patient pays the rest.
        assertThat(result.totalDiscount()).isEqualByComparingTo("50.00");
        assertThat(result.patientPayable()).isEqualByComparingTo("50.00");
        // < 100% discount is a subsidy allocation, with a rule trace.
        assertThat(result.subsidyPayable()).isEqualByComparingTo("50.00");
        assertThat(result.allocations())
                .anySatisfy(a -> {
                    assertThat(a.partyType()).isEqualTo("SUBSIDY");
                    assertThat(a.reasonCodes()).contains("EXEMPTION_HEALTH_WORKER");
                });
    }

    @Test
    void capBoundsTheDiscountOnLargeBills() {
        when(exemptionRuleRepository.findEffective(eq(tenantId), eq(ExemptionCategory.HEALTH_WORKER), any()))
                .thenReturn(List.of(rule("50.00", "200.00")));

        BillSplitResult result = engine.split(
                tenantId, new BigDecimal("1000.00"), "HEALTH_WORKER", null, Map.of());

        // 50% would be 500.00 but the cap clamps at 200.00.
        assertThat(result.totalDiscount()).isEqualByComparingTo("200.00");
        assertThat(result.patientPayable()).isEqualByComparingTo("800.00");
    }

    @Test
    void disabledRuleMeansNoDiscount() {
        // findEffective only returns ACTIVE rules; a DISABLED governed seed row
        // is therefore invisible and the bill splits with zero discount.
        when(exemptionRuleRepository.findEffective(eq(tenantId), eq(ExemptionCategory.HEALTH_WORKER), any()))
                .thenReturn(List.of());

        BillSplitResult result = engine.split(
                tenantId, new BigDecimal("100.00"), "HEALTH_WORKER", null, Map.of());

        assertThat(result.totalDiscount()).isEqualByComparingTo("0");
        assertThat(result.patientPayable()).isEqualByComparingTo("100.00");
    }

    @Test
    void fullExemptionAllocatesWriteOff() {
        when(exemptionRuleRepository.findEffective(eq(tenantId), eq(ExemptionCategory.HEALTH_WORKER), any()))
                .thenReturn(List.of(rule("100.00", null)));

        BillSplitResult result = engine.split(
                tenantId, new BigDecimal("80.00"), "HEALTH_WORKER", null, Map.of());

        assertThat(result.patientPayable()).isEqualByComparingTo("0");
        assertThat(result.writeOff()).isEqualByComparingTo("80.00");
    }
}
