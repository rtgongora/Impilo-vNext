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
 * Early pregnancy danger signs.
 *
 * <p>These tests are written around the two deaths this pack exists to prevent, and both are deaths
 * of women who were seen. The first is the ruptured ectopic in a woman whose pregnancy was never
 * confirmed. The second is the woman discharged on a scan that showed nothing — because a report
 * containing no abnormality reads as a normal report.
 */
class EarlyPregnancyDangerTest {

    private static final String PACK = "clinical/rmnp-early-pregnancy-danger.json";

    private final RuleContentLoader loader = new RuleContentLoader(new ObjectMapper());
    private final MaternalDangerSignService service = new MaternalDangerSignService(loader);

    private static Map<String, Object> facts(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private List<String> codes(Map<String, Object> f, boolean complete) {
        return service.evaluate(PACK, f, complete).alerts().stream()
                .map(MaternalDangerSignService.Alert::code).toList();
    }

    // ------------------------------------------------- the ungated rule

    @Test
    @DisplayName("shock with abdominal pain fires with NO pregnancy status recorded at all")
    void theEctopicRuleDoesNotWaitForAPregnancyTest() {
        var codes = codes(facts(
                "systolicBp", 84, "pulseRate", 124, "abdominalPain", "PRESENT"), false);

        // The whole point. A rule requiring pregnancyStatus would have excluded the woman this
        // rule was written for — the one who did not know she was pregnant.
        assertThat(codes).contains("EP_SHOCK_WITH_ABDOMINAL_PAIN");
    }

    @Test
    @DisplayName("it fires even when pregnancy is recorded as excluded")
    void aNegativeTestDoesNotSilenceTheEctopicRule() {
        var codes = codes(facts(
                "systolicBp", 80, "pulseRate", 130, "abdominalPain", "PRESENT",
                "pregnancyStatus", "EXCLUDED"), true);

        assertThat(codes).contains("EP_SHOCK_WITH_ABDOMINAL_PAIN");
    }

    @Test
    @DisplayName("it is non-overridable and demands resuscitation before a scan")
    void theEctopicAlertCannotBeDismissed() {
        var alert = service.evaluate(PACK, facts(
                        "systolicBp", 84, "pulseRate", 124, "abdominalPain", "PRESENT"), false)
                .alerts().stream()
                .filter(a -> "EP_SHOCK_WITH_ABDOMINAL_PAIN".equals(a.code()))
                .findFirst().orElseThrow();

        assertThat(alert.severity()).isEqualTo("CRITICAL");
        assertThat(alert.interruptive()).isTrue();
        assertThat(alert.overrideAllowed()).isFalse();
        assertThat(alert.requiredAction()).contains("do not wait for it before resuscitating");
        assertThat(alert.requiredAction()).contains("Do not send her for an outpatient scan");
    }

    // ------------------------------------------------- the scan that reads as normal

    @Test
    @DisplayName("a positive test with nothing seen on scan raises an alert, not silence")
    void anEmptyScanIsNotANormalScan() {
        var codes = codes(facts(
                "pregnancyStatus", "CONFIRMED",
                "ultrasoundIntrauterinePregnancy", "NOT_SEEN"), true);

        assertThat(codes).contains("EP_PREGNANCY_OF_UNKNOWN_LOCATION");
    }

    @Test
    @DisplayName("no scan done is not the same as a scan showing nothing")
    void notDoneIsNotNotSeen() {
        assertThat(codes(facts(
                "pregnancyStatus", "CONFIRMED",
                "ultrasoundIntrauterinePregnancy", "NOT_DONE"), true))
                .doesNotContain("EP_PREGNANCY_OF_UNKNOWN_LOCATION");
    }

    @Test
    @DisplayName("the unlocated-pregnancy alert refuses discharge without a follow-up plan")
    void theUnlocatedPregnancyAlertNamesTheFollowUp() {
        var alert = service.evaluate(PACK, facts(
                        "pregnancyStatus", "CONFIRMED",
                        "ultrasoundIntrauterinePregnancy", "NOT_SEEN"), true)
                .alerts().stream()
                .filter(a -> "EP_PREGNANCY_OF_UNKNOWN_LOCATION".equals(a.code()))
                .findFirst().orElseThrow();

        assertThat(alert.message()).contains("not a normal result");
        assertThat(alert.requiredAction()).contains("Do not discharge without a follow-up plan");
    }

    @Test
    @DisplayName("bleeding with a closed os is not reassured until the pregnancy is located")
    void reassuranceWaitsForTheScan() {
        var unlocated = codes(facts(
                "pregnancyStatus", "CONFIRMED", "cervicalOsState", "CLOSED",
                "vaginalBleeding", "LIGHT",
                "ultrasoundIntrauterinePregnancy", "NOT_DONE"), true);
        var located = codes(facts(
                "pregnancyStatus", "CONFIRMED", "cervicalOsState", "CLOSED",
                "vaginalBleeding", "LIGHT",
                "ultrasoundIntrauterinePregnancy", "SEEN"), true);

        assertThat(unlocated).contains("EP_THREATENED_MISCARRIAGE_LOCATE_FIRST");
        assertThat(located).doesNotContain("EP_THREATENED_MISCARRIAGE_LOCATE_FIRST");
    }

    // ------------------------------------------------- silence is not reassurance

    @Test
    @DisplayName("an empty alert list with screening incomplete is NOT 'no danger signs'")
    void anEmptyListIsNotAClearAssessment() {
        var assessment = service.evaluate(PACK, facts(), false);

        assertThat(assessment.alerts()).isEmpty();
        assertThat(assessment.noDangerSignsFound())
                .as("nothing was asked; this must not read as a clear assessment")
                .isFalse();
        assertThat(assessment.incomplete()).isTrue();
        assertThat(assessment.note()).contains("Nothing has been ruled out");
    }

    @Test
    @DisplayName("screeningComplete alone does not make an unfinished assessment clear")
    void assertingCompletionDoesNotOverrideUnresolvedRules() {
        // The caller says screening is complete, but findings the rules need are absent, so rules
        // could not resolve. A claim of completeness must not paper over that.
        var assessment = service.evaluate(PACK, facts("pregnancyStatus", "CONFIRMED"), true);

        assertThat(assessment.incomplete()).isTrue();
        assertThat(assessment.noDangerSignsFound()).isFalse();
        assertThat(assessment.notAssessed()).isNotEmpty();
    }

    @Test
    @DisplayName("a fully answered, genuinely negative assessment DOES read as no danger signs")
    void acompleteNegativeAssessmentIsAllowedToSaySo() {
        var assessment = service.evaluate(PACK, facts(
                "systolicBp", 118, "diastolicBp", 74, "pulseRate", 76, "temperature", 36.8,
                "pregnancyStatus", "CONFIRMED",
                "abdominalPain", "ABSENT", "abdominalPainSeverity", "MILD",
                "shoulderTipPain", "ABSENT", "syncopeOrCollapse", "ABSENT",
                "vaginalBleeding", "ABSENT", "offensiveVaginalDischarge", "ABSENT",
                "vesiclesPassed", "ABSENT", "persistentVomiting", "ABSENT",
                "unableToKeepFluidsDown", "NO", "urineKetones", "NEGATIVE",
                "cervicalOsState", "CLOSED", "uterineSizeVsDates", "CONSISTENT",
                "ultrasoundIntrauterinePregnancy", "SEEN",
                "recentUterineIntervention", "NO"), true);

        // The positive control. A service that can never say "clear" is one clinicians stop reading.
        assertThat(assessment.alerts()).isEmpty();
        assertThat(assessment.incomplete()).isFalse();
        assertThat(assessment.noDangerSignsFound()).isTrue();
        assertThat(assessment.note()).contains("Screening complete");
    }

    // ------------------------------------------------- prioritisation and coverage

    @Test
    @DisplayName("the most severe alert leads, and there is exactly one top-line action")
    void oneTopLineAction() {
        var assessment = service.evaluate(PACK, facts(
                "systolicBp", 82, "pulseRate", 128, "abdominalPain", "PRESENT",
                "abdominalPainSeverity", "SEVERE",
                "pregnancyStatus", "CONFIRMED", "shoulderTipPain", "PRESENT",
                "vaginalBleeding", "HEAVY"), true);

        // Several fire. A shocked woman needs one instruction, not four of equal weight.
        assertThat(assessment.alerts().size()).isGreaterThan(1);
        assertThat(assessment.alerts().get(0).severity()).isEqualTo("CRITICAL");
        assertThat(assessment.topLineAction())
                .isEqualTo(assessment.alerts().get(0).requiredAction());
    }

    @Test
    @DisplayName("septic miscarriage does not require her to disclose a termination")
    void sepsisIsTreatedWithoutInterrogation() {
        var alert = service.evaluate(PACK, facts(
                        "temperature", 38.7, "vaginalBleeding", "HEAVY"), true)
                .alerts().stream()
                .filter(a -> "EP_SEPTIC_MISCARRIAGE".equals(a.code()))
                .findFirst().orElseThrow();

        assertThat(alert.severity()).isEqualTo("CRITICAL");
        assertThat(alert.requiredAction())
                .contains("the history can wait and is not a condition of care");
    }

    @Test
    @DisplayName("hyperemesis names thiamine before glucose")
    void hyperemesisCarriesThePreventableHarm() {
        var alert = service.evaluate(PACK, facts(
                        "pregnancyStatus", "CONFIRMED",
                        "persistentVomiting", "PRESENT", "urineKetones", "THREE_PLUS"), true)
                .alerts().stream()
                .filter(a -> "EP_HYPEREMESIS".equals(a.code()))
                .findFirst().orElseThrow();

        assertThat(alert.requiredAction()).contains("thiamine before any glucose");
    }

    @Test
    @DisplayName("a rule whose applicability cannot be decided reports its inputs rather than vanishing")
    void anUndecidableGateSurfacesRatherThanSilentlySkipping() {
        // pregnancyStatus is absent, so the gated rules cannot be shown to apply — and must not be
        // quietly dropped either, because "does not apply" and "cannot tell" are different.
        var assessment = service.evaluate(PACK, facts("vaginalBleeding", "HEAVY"), true);

        assertThat(assessment.incomplete()).isTrue();
        assertThat(assessment.notAssessed()).contains("pregnancyStatus");
    }

    @Test
    @DisplayName("a rule that genuinely does not apply contributes nothing")
    void anInapplicableRuleIsNotReportedAsUnassessed() {
        var assessment = service.evaluate(PACK, facts(
                "pregnancyStatus", "EXCLUDED",
                "systolicBp", 118, "pulseRate", 76, "abdominalPain", "ABSENT"), true);

        // Pregnancy is excluded, so the gated early-pregnancy rules do not apply to her. Listing
        // their inputs would send a clinician hunting for observations that are irrelevant.
        assertThat(assessment.notAssessed()).doesNotContain("cervicalOsState", "vesiclesPassed");
    }

    // ------------------------------------------------- content gate

    @TestFactory
    @DisplayName("every rule's own fixtures still behave as its authors described")
    List<DynamicTest> contentFixturesPass() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TabularRule rule : loader.load(PACK)) {
            assertThat(rule.testCases())
                    .as("rule %s ships no fixtures", rule.code()).isNotEmpty();
            for (TabularRule.TestCase testCase : rule.testCases()) {
                tests.add(DynamicTest.dynamicTest(rule.code() + " — " + testCase.name(), () -> {
                    boolean fired = service.evaluate(PACK, testCase.facts(), true).alerts().stream()
                            .anyMatch(a -> a.code().equals(rule.code()));
                    assertThat(fired)
                            .as("%s: %s", rule.code(), testCase.name())
                            .isEqualTo(testCase.expectFires());
                }));
            }
        }
        return tests;
    }

    @Test
    @DisplayName("every rule states an action, cites a source and carries provenance")
    void everyRuleIsActionableAndAttributable() {
        var rules = loader.load(PACK);
        assertThat(rules).isNotEmpty();
        for (TabularRule rule : rules) {
            assertThat(rule.requiredAction()).as("%s has no action", rule.code()).isNotBlank();
            assertThat(rule.sourceRefs()).as("%s cites nothing", rule.code()).isNotEmpty();
            assertThat(rule.requiredInputs()).as("%s declares no inputs", rule.code()).isNotEmpty();
            assertThat(rule.provenance()).as("%s has no provenance", rule.code()).isNotNull();
        }
    }

    @Test
    @DisplayName("every CRITICAL rule is interruptive and cannot be overridden")
    void criticalAlertsAreNotPreferences() {
        for (TabularRule rule : loader.load(PACK)) {
            if ("CRITICAL".equals(rule.severity())) {
                assertThat(rule.interruptive())
                        .as("%s is CRITICAL but not interruptive", rule.code()).isTrue();
                assertThat(rule.overrideAllowed())
                        .as("%s is CRITICAL but dismissible", rule.code()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("missing content reports unavailable, never that she is fine")
    void missingContentIsNeverSilence() {
        var assessment = service.evaluate("clinical/does-not-exist.json", facts(), true);

        assertThat(assessment.noDangerSignsFound()).isFalse();
        assertThat(assessment.incomplete()).isTrue();
        assertThat(assessment.contentProblems()).isNotEmpty();
        assertThat(assessment.note()).contains("not a statement that there are no danger signs");
    }
}
