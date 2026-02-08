package zw.gov.mohcc.impilo.costa.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.ChargingRulesetEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.BillLineKind;
import zw.gov.mohcc.impilo.costa.domain.enums.RulesetStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.ChargingRulesetRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargingRuleEngineTest {

    @Mock
    private ChargingRulesetRepository rulesetRepository;

    private ChargingRuleEngine engine;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        engine = new ChargingRuleEngine(rulesetRepository, objectMapper);
    }

    @Test
    void shouldPassThroughWhenNoRulesetsExist() {
        when(rulesetRepository.findEffective(eq(tenantId), any())).thenReturn(List.of());

        RuleContext ctx = new RuleContext(tenantId, facilityId, null, null,
                "CONSULT", BillLineKind.SERVICE, BigDecimal.ONE,
                new BigDecimal("100.00"), "OUTPATIENT", LocalDateTime.now(),
                false, false, Map.of());

        RuleResult result = engine.evaluate(ctx);

        assertEquals(new BigDecimal("100.00"), result.chargeAmount());
        assertFalse(result.excluded());
    }

    @Test
    void shouldApplyEmergencySurcharge() {
        String rulesJson = """
                [{"name":"Emergency surcharge","priority":10,"conditions":{"is_emergency":true},"action":{"type":"MARKUP_PCT","value":25.0}}]
                """;

        ChargingRulesetEntity ruleset = createRuleset(rulesJson);
        when(rulesetRepository.findEffective(eq(tenantId), any())).thenReturn(List.of(ruleset));

        RuleContext ctx = new RuleContext(tenantId, facilityId, null, null,
                "CONSULT", BillLineKind.SERVICE, BigDecimal.ONE,
                new BigDecimal("100.00"), "EMERGENCY", LocalDateTime.now(),
                true, false, Map.of());

        RuleResult result = engine.evaluate(ctx);

        assertEquals(new BigDecimal("125.00"), result.chargeAmount());
        assertTrue(result.appliedRules().contains("Emergency surcharge"));
    }

    @Test
    void shouldExcludeConsultationFeeWhenAdmitted() {
        String rulesJson = """
                [{"name":"Admitted consult waiver","priority":5,"conditions":{"is_admitted":true,"kind":"FEE"},"action":{"type":"EXCLUDE"}}]
                """;

        ChargingRulesetEntity ruleset = createRuleset(rulesJson);
        when(rulesetRepository.findEffective(eq(tenantId), any())).thenReturn(List.of(ruleset));

        RuleContext ctx = new RuleContext(tenantId, facilityId, null, null,
                "CONSULT-FEE", BillLineKind.FEE, BigDecimal.ONE,
                new BigDecimal("50.00"), "INPATIENT", LocalDateTime.now(),
                false, true, Map.of());

        RuleResult result = engine.evaluate(ctx);

        assertTrue(result.excluded());
        assertEquals(BigDecimal.ZERO, result.chargeAmount());
    }

    @Test
    void shouldApplyPrivatePatientMarkup() {
        String rulesJson = """
                [{"name":"Private markup","priority":5,"conditions":{"patient_category":"PRIVATE"},"action":{"type":"MARKUP_PCT","value":50.0}}]
                """;

        ChargingRulesetEntity ruleset = createRuleset(rulesJson);
        when(rulesetRepository.findEffective(eq(tenantId), any())).thenReturn(List.of(ruleset));

        RuleContext ctx = new RuleContext(tenantId, facilityId, null, "PRIVATE",
                "BEDDAY", BillLineKind.BEDDAY, new BigDecimal("5"),
                new BigDecimal("250.00"), "INPATIENT", LocalDateTime.now(),
                false, true, Map.of());

        RuleResult result = engine.evaluate(ctx);

        assertEquals(new BigDecimal("375.00"), result.chargeAmount());
    }

    @Test
    void shouldApplyRulesInPriorityOrder() {
        String rulesJson = """
                [
                  {"name":"Base markup","priority":1,"conditions":{},"action":{"type":"MARKUP_PCT","value":10.0}},
                  {"name":"Discount for volume","priority":2,"conditions":{"min_qty":"10"},"action":{"type":"DISCOUNT_PCT","value":5.0}}
                ]
                """;

        ChargingRulesetEntity ruleset = createRuleset(rulesJson);
        when(rulesetRepository.findEffective(eq(tenantId), any())).thenReturn(List.of(ruleset));

        RuleContext ctx = new RuleContext(tenantId, facilityId, null, null,
                "SUPPLY-001", BillLineKind.PRODUCT, new BigDecimal("20"),
                new BigDecimal("100.00"), "OUTPATIENT", LocalDateTime.now(),
                false, false, Map.of());

        RuleResult result = engine.evaluate(ctx);

        // 100 + 10% = 110, then 110 - 5% = 104.50
        assertEquals(new BigDecimal("104.50"), result.chargeAmount());
        assertEquals(2, result.appliedRules().size());
    }

    private ChargingRulesetEntity createRuleset(String rulesJson) {
        ChargingRulesetEntity ruleset = new ChargingRulesetEntity();
        ruleset.setId(1L);
        ruleset.setTenantId(tenantId);
        ruleset.setName("test-rules");
        ruleset.setStatus(RulesetStatus.PUBLISHED);
        ruleset.setRules(rulesJson);
        ruleset.setEffectiveFrom(LocalDate.now().minusDays(30));
        return ruleset;
    }
}
