package zw.gov.mohcc.impilo.clinical.danger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;
import zw.gov.mohcc.impilo.clinical.rules.tabular.TabularRule;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The governance gate for clinical content.
 *
 * <p>Every rule ships with its own fixtures, and this executes them against the live
 * evaluator. A rule whose own examples no longer behave as its authors described cannot
 * reach a patient: the build fails first. That is what makes editing a threshold a safe
 * content change rather than an unreviewable one.</p>
 */
class PaediatricRuleContentTest {

    private final RuleContentLoader loader = new RuleContentLoader(new ObjectMapper());
    private final DangerSignEvaluationService service = new DangerSignEvaluationService(loader);

    private List<TabularRule> rules() {
        return loader.load(DangerSignEvaluationService.CONTENT_PATH);
    }

    @Test
    void theDangerSignPackLoads() {
        assertThat(rules()).isNotEmpty();
    }

    @TestFactory
    List<DynamicTest> everyRulesOwnFixturesStillBehaveAsDescribed() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TabularRule rule : rules()) {
            assertThat(rule.testCases())
                    .as("rule %s ships no fixtures, so a change to it could not be caught", rule.code())
                    .isNotEmpty();

            for (TabularRule.TestCase testCase : rule.testCases()) {
                tests.add(DynamicTest.dynamicTest(rule.code() + " — " + testCase.name(), () -> {
                    Integer ageDays = ageOf(testCase);
                    var assessment = service.evaluate(ageDays, testCase.facts());
                    boolean fired = assessment.alerts().stream()
                            .anyMatch(alert -> alert.code().equals(rule.code()));

                    assertThat(fired)
                            .as("%s: %s", rule.code(), testCase.name())
                            .isEqualTo(testCase.expectFires());

                    if (testCase.expectIncomplete()) {
                        assertThat(assessment.incomplete())
                                .as("%s: %s should report unassessed signs", rule.code(), testCase.name())
                                .isTrue();
                    }
                }));
            }
        }
        return tests;
    }

    @Test
    void everyRuleStatesWhoMustRatifyItAndWhereItCameFrom() {
        for (TabularRule rule : rules()) {
            assertThat(rule.requiredAction())
                    .as("%s must tell a clinician what to do, not only what was found", rule.code())
                    .isNotBlank();
            assertThat(rule.sourceRefs())
                    .as("%s must cite its guideline source", rule.code())
                    .isNotEmpty();
            assertThat(rule.approvalStatus())
                    .as("%s must declare its approval status", rule.code())
                    .isNotBlank();
            assertThat(rule.requiredInputs())
                    .as("%s must declare the observations it needs, so unassessed ones can be reported",
                            rule.code())
                    .isNotEmpty();
        }
    }

    @Test
    void nothingInThisPackClaimsToBeRatifiedYet() {
        // If a rule ever legitimately becomes APPROVED this test should be updated with the
        // ratification record — it must not be possible for content to quietly claim approval.
        assertThat(rules()).allSatisfy(rule ->
                assertThat(rule.ratified())
                        .as("%s claims ratification; confirm the MoHCC sign-off before relaxing this",
                                rule.code())
                        .isFalse());
    }

    @Test
    void ruleAgeWindowsAreCoherent() {
        for (TabularRule rule : rules()) {
            if (rule.ageMinDays() != null && rule.ageMaxDays() != null) {
                assertThat(rule.ageMinDays())
                        .as("%s has an inverted age window", rule.code())
                        .isLessThanOrEqualTo(rule.ageMaxDays());
            }
        }
    }

    @Test
    void everyCriticalRuleInterruptsAndCannotBeWavedAway() {
        for (TabularRule rule : rules()) {
            if ("CRITICAL".equalsIgnoreCase(rule.severity())) {
                assertThat(rule.interruptive())
                        .as("%s is critical but would not interrupt", rule.code())
                        .isTrue();
                assertThat(rule.overrideAllowed())
                        .as("%s is critical but could be dismissed", rule.code())
                        .isFalse();
            }
        }
    }

    private static Integer ageOf(TabularRule.TestCase testCase) {
        Object age = testCase.facts().get("ageDays");
        return age instanceof Number number ? number.intValue() : null;
    }
}
