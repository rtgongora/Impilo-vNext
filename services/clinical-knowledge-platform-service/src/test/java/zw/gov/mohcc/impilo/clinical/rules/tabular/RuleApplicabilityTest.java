package zw.gov.mohcc.impilo.clinical.rules.tabular;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether a rule is <em>about</em> a patient, which is a different question from whether it fires.
 *
 * <p>Both mechanisms here exist because the platform is about to hold rules that are not scoped by
 * the patient's age. Contraceptive eligibility turns on medical conditions, Robson classification on
 * parity and presentation, postnatal rules on the day after birth. Under the previous behaviour
 * every one of them would have silently applied to nobody.
 */
class RuleApplicabilityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static TabularRule rule(Integer ageMinDays, Integer ageMaxDays, String appliesWhenJson) {
        JsonNode appliesWhen = null;
        if (appliesWhenJson != null) {
            try {
                appliesWhen = MAPPER.readTree(appliesWhenJson);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return new TabularRule("TEST_RULE", "Test rule", "DANGER", "ALERT", "HIGH",
                true, false, true, ageMinDays, ageMaxDays, appliesWhen,
                List.of("someInput"), MAPPER.createObjectNode(), "message", "explanation",
                "do something", null, List.of("source"), "ENGINEERING_SEED",
                "PENDING_MOHCC_RATIFICATION", "test-1.0.0", null, List.of(), null, 0);
    }

    @Test
    void aRuleWithNoAgeWindowAppliesEvenWhenTheAgeIsUnknown() {
        // It never claimed to be about age. Contraceptive eligibility and Robson classification are
        // both like this: they turn on conditions, parity and presentation, never on how old the
        // woman is. The previous behaviour — unknown age matches nothing — would have made every
        // such rule inert, and inert in the quietest possible way: no error, no alert, no trace.
        assertThat(rule(null, null, null).appliesToAge(null)).isTrue();
        assertThat(rule(null, null, null).appliesToAge(9000)).isTrue();
    }

    @Test
    void aRuleThatDeclaresAnAgeWindowStillMatchesNothingWithoutAnAge() {
        // Unchanged, and it must stay unchanged: if a rule says it is about the first 59 days of
        // life, and we do not know how old the patient is, we cannot tell whether it covers them.
        // Applying it anyway would run young-infant thresholds against a ten-year-old.
        assertThat(rule(0, 59, null).appliesToAge(null)).isFalse();
        assertThat(rule(0, 59, null).appliesToAge(30)).isTrue();
        assertThat(rule(0, 59, null).appliesToAge(900)).isFalse();
    }

    @Test
    void anOpenEndedWindowStillBehavesAsBefore() {
        assertThat(rule(60, null, null).appliesToAge(30)).isFalse();
        assertThat(rule(60, null, null).appliesToAge(9000)).isTrue();
        assertThat(rule(60, null, null).appliesToAge(null)).isFalse();
    }

    @Test
    void everyShippedPaediatricRuleDeclaresAnAgeWindow() {
        // The safety argument for the change above rests on this. If a shipped paediatric rule had
        // no age window, it would have been inert until now and would suddenly start applying to
        // everyone — including adults. None does, so the change cannot alter existing behaviour;
        // this test is what keeps that true as content is added.
        RuleContentLoader loader = new RuleContentLoader(MAPPER);
        List<TabularRule> rules = loader.load("clinical/paediatric-danger-signs.json");
        assertThat(rules).isNotEmpty();
        assertThat(rules)
                .as("a paediatric rule with no age window would apply to adults")
                .allSatisfy(r -> assertThat(r.ageMinDays() != null || r.ageMaxDays() != null)
                        .as("rule %s declares no age window", r.code())
                        .isTrue());
    }

    @Test
    void noShippedRuleUsesAppliesWhenYet() {
        // Documents the starting state. The gate is new and unused, so its introduction cannot have
        // changed how any live rule behaves — which is the claim the paediatric re-proof rests on.
        RuleContentLoader loader = new RuleContentLoader(MAPPER);
        assertThat(loader.load("clinical/paediatric-danger-signs.json"))
                .allSatisfy(r -> assertThat(r.appliesWhen()).isNull());
    }
}
