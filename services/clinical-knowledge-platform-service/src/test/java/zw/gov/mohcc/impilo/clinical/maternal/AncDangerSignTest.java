package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;
import zw.gov.mohcc.impilo.clinical.rules.tabular.TabularRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Antenatal danger signs, and the hypertensive-disorders ladder.
 *
 * <p>The property the ladder tests defend: when a woman is convulsing, the screen shows eclampsia —
 * not eclampsia plus severe pre-eclampsia plus pre-eclampsia plus gestational hypertension, four
 * alarms for one disease with the fatal one buried in the middle. The milder rungs are not deleted;
 * they move to a superseded list, because "we also saw the earlier stage" is true and worth keeping.
 */
class AncDangerSignTest {

    private static final String PACK = "clinical/rmnp-anc-danger-signs.json";

    private final RuleContentLoader loader = new RuleContentLoader(new ObjectMapper());
    private final MaternalDangerSignService service = new MaternalDangerSignService(loader);

    private static Map<String, Object> facts(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private List<String> activeCodes(Map<String, Object> f) {
        return service.evaluate(PACK, f, true).alerts().stream()
                .map(MaternalDangerSignService.Alert::code).toList();
    }

    private List<String> supersededCodes(Map<String, Object> f) {
        return service.evaluate(PACK, f, true).supersededAlerts().stream()
                .map(MaternalDangerSignService.Alert::code).toList();
    }

    // ---------------------------------------------------------------- the ladder

    @Test
    @DisplayName("a convulsing woman shows eclampsia alone — the milder rungs are superseded, not shown")
    void eclampsiaSuppressesTheMilderRungs() {
        // Everything is true at once: convulsing, severe BP, proteinuria — the full ladder fires.
        var f = facts(
                "gestationalAgeWeeks", 34,
                "convulsions", "PRESENT",
                "systolicBp", 170, "diastolicBp", 115,
                "severeHeadache", "PRESENT", "visualDisturbance", "PRESENT",
                "epigastricPain", "PRESENT", "proteinuria", "THREE_PLUS");

        var active = activeCodes(f);
        var superseded = supersededCodes(f);

        // Exactly one hypertensive signal is active, and it is the eclampsia.
        assertThat(active).contains("ANC_HTN_ECLAMPSIA");
        assertThat(active).doesNotContain(
                "ANC_HTN_SEVERE_PREECLAMPSIA", "ANC_HTN_PREECLAMPSIA", "ANC_HTN_GESTATIONAL");
        // The milder rungs are kept, not discarded — visible in the superseded list.
        assertThat(superseded).contains(
                "ANC_HTN_SEVERE_PREECLAMPSIA", "ANC_HTN_PREECLAMPSIA");
    }

    @Test
    @DisplayName("severe pre-eclampsia suppresses pre-eclampsia and gestational hypertension below it")
    void severeSuppressesTheLowerRungs() {
        var f = facts(
                "gestationalAgeWeeks", 30,
                "convulsions", "ABSENT",
                "systolicBp", 165, "diastolicBp", 112,
                "proteinuria", "TWO_PLUS");

        assertThat(activeCodes(f)).contains("ANC_HTN_SEVERE_PREECLAMPSIA");
        assertThat(activeCodes(f)).doesNotContain("ANC_HTN_PREECLAMPSIA", "ANC_HTN_GESTATIONAL");
        assertThat(supersededCodes(f)).contains("ANC_HTN_PREECLAMPSIA");
    }

    @Test
    @DisplayName("the top-line action of a convulsing woman is the eclampsia action")
    void topLineIsTheMostSevereRung() {
        var assessment = service.evaluate(PACK, facts(
                "gestationalAgeWeeks", 34, "convulsions", "PRESENT",
                "systolicBp", 170, "diastolicBp", 115, "proteinuria", "THREE_PLUS"), true);

        assertThat(assessment.topLineAction()).contains("magnesium sulfate");
        assertThat(assessment.alerts().get(0).code()).isEqualTo("ANC_HTN_ECLAMPSIA");
    }

    @Test
    @DisplayName("gestational hypertension stands alone when it is the only rung firing")
    void mildestRungShowsWhenAlone() {
        var f = facts(
                "gestationalAgeWeeks", 30,
                "systolicBp", 146, "diastolicBp", 94, "proteinuria", "NEGATIVE",
                "convulsions", "ABSENT");

        assertThat(activeCodes(f)).contains("ANC_HTN_GESTATIONAL");
        assertThat(supersededCodes(f)).isEmpty();
    }

    @Test
    @DisplayName("proteinuria moves her from gestational hypertension to pre-eclampsia — not both")
    void proteinuriaMovesHerUpTheLadderExclusively() {
        var withProtein = activeCodes(facts(
                "gestationalAgeWeeks", 30, "systolicBp", 146, "diastolicBp", 94,
                "proteinuria", "TWO_PLUS", "convulsions", "ABSENT"));

        assertThat(withProtein).contains("ANC_HTN_PREECLAMPSIA");
        assertThat(withProtein).doesNotContain("ANC_HTN_GESTATIONAL");
    }

    // ---------------------------------------------------------------- other families are independent

    @Test
    @DisplayName("a bleeding emergency and a hypertensive emergency are different families and both show")
    void differentFamiliesBothSurface() {
        var active = activeCodes(facts(
                "gestationalAgeWeeks", 32,
                "convulsions", "PRESENT",
                "vaginalBleeding", "HEAVY"));

        // Eclampsia and antepartum haemorrhage are separate diseases; suppressing one for the other
        // would be the opposite mistake. Both must show.
        assertThat(active).contains("ANC_HTN_ECLAMPSIA", "ANC_ANTEPARTUM_HAEMORRHAGE");
    }

    @Test
    @DisplayName("antepartum haemorrhage forbids a vaginal examination in its action")
    void aphCarriesTheNoVaginalExamInstruction() {
        var alert = service.evaluate(PACK, facts(
                        "gestationalAgeWeeks", 32, "vaginalBleeding", "HEAVY"), true)
                .alerts().stream().filter(a -> "ANC_ANTEPARTUM_HAEMORRHAGE".equals(a.code()))
                .findFirst().orElseThrow();
        assertThat(alert.requiredAction()).containsIgnoringCase("do not perform a vaginal examination");
    }

    @Test
    @DisplayName("reduced fetal movements demands same-day assessment, not the next contact")
    void reducedMovementsIsNotDeferred() {
        var alert = service.evaluate(PACK, facts(
                        "gestationalAgeWeeks", 34, "reducedFetalMovements", "PRESENT"), true)
                .alerts().stream().filter(a -> "ANC_REDUCED_FETAL_MOVEMENTS".equals(a.code()))
                .findFirst().orElseThrow();
        assertThat(alert.requiredAction()).containsIgnoringCase("same day");
    }

    // ---------------------------------------------------------------- gating and silence

    @Test
    @DisplayName("the pre-eclampsia rules do not apply before 20 weeks")
    void preeclampsiaGatedByGestation() {
        var f = facts(
                "gestationalAgeWeeks", 14, "systolicBp", 146, "diastolicBp", 94,
                "proteinuria", "TWO_PLUS", "convulsions", "ABSENT");
        assertThat(activeCodes(f)).doesNotContain("ANC_HTN_PREECLAMPSIA", "ANC_HTN_GESTATIONAL");
    }

    @Test
    @DisplayName("eclampsia is NOT gestation-gated — a convulsion is an emergency at any gestation")
    void eclampsiaIsNotGestationGated() {
        assertThat(activeCodes(facts("gestationalAgeWeeks", 10, "convulsions", "PRESENT")))
                .contains("ANC_HTN_ECLAMPSIA");
    }

    @Test
    @DisplayName("an empty assessment with screening incomplete is not 'no danger signs'")
    void silenceIsNotReassurance() {
        var assessment = service.evaluate(PACK, facts("gestationalAgeWeeks", 30), false);
        assertThat(assessment.noDangerSignsFound()).isFalse();
    }

    // ---------------------------------------------------------------- content gate

    @TestFactory
    @DisplayName("every rule's own fixtures behave as described")
    List<DynamicTest> contentFixturesPass() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TabularRule rule : loader.load(PACK)) {
            assertThat(rule.testCases()).as("%s ships no fixtures", rule.code()).isNotEmpty();
            for (TabularRule.TestCase tc : rule.testCases()) {
                tests.add(DynamicTest.dynamicTest(rule.code() + " — " + tc.name(), () -> {
                    // Evaluate against the rule in isolation of ladder suppression, since a fixture
                    // asserts whether the rule ITSELF fires, before family logic.
                    boolean fired = service.evaluate(PACK, tc.facts(), true).alerts().stream()
                            .anyMatch(a -> a.code().equals(rule.code()))
                            || service.evaluate(PACK, tc.facts(), true).supersededAlerts().stream()
                            .anyMatch(a -> a.code().equals(rule.code()));
                    assertThat(fired).as("%s: %s", rule.code(), tc.name())
                            .isEqualTo(tc.expectFires());
                }));
            }
        }
        return tests;
    }

    @Test
    @DisplayName("every ladder rung has a distinct rank and shares the family")
    void ladderRanksAreDistinct() {
        var rungs = loader.load(PACK).stream()
                .filter(r -> "HYPERTENSIVE_DISORDERS".equals(r.signalFamily())).toList();
        assertThat(rungs).hasSize(4);
        var ranks = rungs.stream().map(TabularRule::signalRank).distinct().toList();
        assertThat(ranks).as("two rungs share a rank; suppression order would be ambiguous")
                .hasSize(4);
    }

    @Test
    @DisplayName("every CRITICAL rule is interruptive and non-overridable")
    void criticalIsNotAPreference() {
        for (TabularRule rule : loader.load(PACK)) {
            if ("CRITICAL".equals(rule.severity())) {
                assertThat(rule.interruptive()).as("%s CRITICAL not interruptive", rule.code()).isTrue();
                assertThat(rule.overrideAllowed()).as("%s CRITICAL dismissible", rule.code()).isFalse();
            }
        }
    }
}
