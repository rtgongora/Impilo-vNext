package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceRuleEntity;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WorkspaceEligibilityEngine}.
 *
 * <p>Validates workspace access rules:
 * - No rules = everyone eligible
 * - Required rules must all match
 * - Optional rules do not block eligibility
 * - ACTOR_TYPE rules match on actor type
 * - ROLE rules match on role membership</p>
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceEligibilityEngineTest {

    @InjectMocks
    private WorkspaceEligibilityEngine engine;

    @Test
    void eligible_noRules_anyoneEligible() {
        boolean result = engine.calculateEligibility(
                "DOCTOR", Set.of("PRESCRIBER"), Set.of(), Collections.emptyList());

        assertThat(result).isTrue();
    }

    @Test
    void eligible_matchingActorType_eligible() {
        WorkspaceRuleEntity rule = createRule("ACTOR_TYPE", "DOCTOR", null, null, true);

        boolean result = engine.calculateEligibility(
                "DOCTOR", Set.of(), Set.of(), List.of(rule));

        assertThat(result).isTrue();
    }

    @Test
    void eligible_nonMatchingActorType_ineligible() {
        WorkspaceRuleEntity rule = createRule("ACTOR_TYPE", "NURSE", null, null, true);

        boolean result = engine.calculateEligibility(
                "DOCTOR", Set.of(), Set.of(), List.of(rule));

        assertThat(result).isFalse();
    }

    @Test
    void eligible_matchingRole_eligible() {
        WorkspaceRuleEntity rule = createRule("ROLE", null, "PRESCRIBER", null, true);

        boolean result = engine.calculateEligibility(
                "DOCTOR", Set.of("PRESCRIBER", "VIEWER"), Set.of(), List.of(rule));

        assertThat(result).isTrue();
    }

    @Test
    void eligible_missingRequiredRole_ineligible() {
        WorkspaceRuleEntity rule = createRule("ROLE", null, "ADMIN", null, true);

        boolean result = engine.calculateEligibility(
                "DOCTOR", Set.of("PRESCRIBER"), Set.of(), List.of(rule));

        assertThat(result).isFalse();
    }

    @Test
    void eligible_multipleRules_allMustMatch() {
        WorkspaceRuleEntity typeRule = createRule("ACTOR_TYPE", "DOCTOR", null, null, true);
        WorkspaceRuleEntity roleRule = createRule("ROLE", null, "PRESCRIBER", null, true);
        WorkspaceRuleEntity privRule = createRule("PRIVILEGE", null, null, "ORDER_LAB", true);

        boolean result = engine.calculateEligibility(
                "DOCTOR", Set.of("PRESCRIBER"), Set.of("ORDER_LAB"),
                List.of(typeRule, roleRule, privRule));

        assertThat(result).isTrue();
    }

    @Test
    void eligible_optionalRule_doesNotBlock() {
        WorkspaceRuleEntity optionalRule = createRule("ROLE", null, "ADMIN", null, false);

        boolean result = engine.calculateEligibility(
                "DOCTOR", Set.of("PRESCRIBER"), Set.of(), List.of(optionalRule));

        assertThat(result).isTrue();
    }

    // ---- Helper ----

    private WorkspaceRuleEntity createRule(String ruleType, String actorType,
                                            String role, String privilege, boolean required) {
        WorkspaceRuleEntity rule = new WorkspaceRuleEntity();
        rule.setRuleType(ruleType);
        rule.setActorType(actorType);
        rule.setRole(role);
        rule.setPrivilege(privilege);
        rule.setRequired(required);
        return rule;
    }
}
