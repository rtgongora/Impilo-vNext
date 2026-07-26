package zw.gov.mohcc.impilo.clinical.danger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code appliesWhen} gate — whether a rule is about this patient at all.
 *
 * <p>The three outcomes are deliberately different, and the middle one is the whole reason the gate
 * is three-valued rather than a boolean:
 *
 * <ul>
 *   <li><b>FALSE</b> — the rule is not about this patient. Nothing outstanding; a postpartum rule
 *       has no business appearing on a woman who has not given birth.</li>
 *   <li><b>UNKNOWN</b> — we could not tell. The rule cannot fire, but its inputs are reported
 *       unassessed. Dropping it silently would produce a clean-looking assessment of a woman nobody
 *       dated, which reads exactly like an assessment that found nothing wrong.</li>
 *   <li><b>TRUE</b> — the rule applies and is evaluated normally.</li>
 * </ul>
 */
class AppliesWhenGateTest {

    private static final String PACK = "clinical/test-applies-when-rules.json";

    private final DangerSignEvaluationService service =
            new DangerSignEvaluationService(new RuleContentLoader(new ObjectMapper()));

    @Test
    void aGateThatIsFalseMakesTheRuleNotApplyAndLeavesNothingOutstanding() {
        var assessment = service.evaluate(PACK, 30, facts(
                "convulsing", "NO"));

        // postpartumDay is absent... but "op": "present" answers that definitively: not present.
        assertThat(codes(assessment)).doesNotContain("TEST_POSTPARTUM_ONLY");
        assertThat(assessment.notAssessed()).doesNotContain("bleedingHeavy");
        assertThat(assessment.incomplete()).isFalse();
    }

    @Test
    void aRuleWithNoGateAndNoAgeWindowIsEvaluatedForAnyAge() {
        var assessment = service.evaluate(PACK, 9000, facts("convulsing", "YES"));

        assertThat(codes(assessment)).contains("TEST_ALWAYS_APPLIES");
        assertThat(assessment.emergency()).isFalse(); // HIGH, not CRITICAL
    }

    @Test
    void thisServiceStillRefusesToAssessAChildOfUnknownAge() {
        // Rule-level applicability and service-level applicability are separate, and this test pins
        // the boundary. TabularRule.appliesToAge(null) now returns true for a rule that declares no
        // age window — but THIS service is the paediatric danger-sign engine, where danger signs
        // differ so sharply by age that applying the wrong set is worse than applying none, so it
        // declines the whole assessment rather than answering partially.
        //
        // The maternal engine will not inherit that guard: a woman's own age is rarely what scopes a
        // maternal rule, and refusing to assess a bleeding woman because her date of birth is
        // missing would be the same mistake in the opposite direction. It gets its own guard, on
        // gestational age, applied per rule rather than to the whole assessment.
        var assessment = service.evaluate(PACK, null, facts("convulsing", "YES"));

        assertThat(assessment.assessed()).isFalse();
        assertThat(assessment.alerts()).isEmpty();
        assertThat(assessment.reason()).contains("age");
    }

    @Test
    void aGatedRuleFiresNormallyOnceTheGateIsSatisfied() {
        var assessment = service.evaluate(PACK, 30, facts(
                "postpartumDay", 1,
                "bleedingHeavy", "YES"));

        assertThat(codes(assessment)).contains("TEST_POSTPARTUM_ONLY");
        assertThat(assessment.emergency()).isTrue();
        assertThat(assessment.referralRequired()).isTrue();
    }

    @Test
    void aGatedRuleThatIsSatisfiedButUnansweredReportsItsInputUnassessed() {
        // She is postpartum, so the rule applies; nobody recorded whether she is bleeding. That is
        // an outstanding question, not a negative finding.
        var assessment = service.evaluate(PACK, 30, facts("postpartumDay", 1));

        assertThat(codes(assessment)).doesNotContain("TEST_POSTPARTUM_ONLY");
        assertThat(assessment.notAssessed()).contains("bleedingHeavy");
        assertThat(assessment.incomplete()).isTrue();
    }

    @Test
    void nothingAssessedIsNeverAnAllClear() {
        // The property the whole engine exists for, restated at the gate: an assessment where
        // nothing was recorded reports no emergency AND reports itself incomplete. A UI may render
        // "no danger signs" only from the second being false.
        var assessment = service.evaluate(PACK, 30, facts("postpartumDay", 1));

        assertThat(assessment.emergency()).isFalse();
        assertThat(assessment.incomplete()).isTrue();
        assertThat(assessment.notAssessed()).isNotEmpty();
    }

    private static Map<String, Object> facts(Object... pairs) {
        Map<String, Object> facts = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            facts.put((String) pairs[i], pairs[i + 1]);
        }
        return facts;
    }

    private static java.util.List<String> codes(DangerSignEvaluationService.DangerSignAssessment a) {
        return a.alerts().stream().map(DangerSignEvaluationService.DangerSignAlert::code).toList();
    }
}
