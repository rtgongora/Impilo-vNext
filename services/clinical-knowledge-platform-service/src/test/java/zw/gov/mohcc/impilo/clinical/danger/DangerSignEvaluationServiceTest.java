package zw.gov.mohcc.impilo.clinical.danger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DangerSignEvaluationServiceTest {

    private final DangerSignEvaluationService service =
            new DangerSignEvaluationService(new RuleContentLoader(new ObjectMapper()));

    @Test
    void aTenDayOldWithHypothermiaAndPoorFeedingRaisesPossibleSeriousBacterialInfection() {
        // The Definition-of-Done journey 2 case. In the first two months the signs of severe
        // infection are few and non-specific, so any one of them is treated as serious.
        var assessment = service.evaluate(10, facts(
                "feeding", "POOR",
                "vitals", Map.of("temperature", 35.0)));

        assertThat(assessment.emergency()).isTrue();
        assertThat(codes(assessment)).contains("PSBI_YOUNG_INFANT");

        var alert = assessment.alerts().stream()
                .filter(a -> a.code().equals("PSBI_YOUNG_INFANT")).findFirst().orElseThrow();
        assertThat(alert.referralRequired()).isTrue();
        assertThat(alert.overrideAllowed()).isFalse();
        assertThat(alert.requiredAction()).contains("antibiotics");
        assertThat(alert.sourceRefs()).isNotEmpty();
        assertThat(alert.contentVersion()).isNotBlank();
    }

    @Test
    void theYoungInfantRuleDoesNotApplyToAToddler() {
        // Applying young-infant thresholds to a two-year-old would flag every mild fever.
        var assessment = service.evaluate(700, facts("vitals", Map.of("temperature", 37.8)));

        assertThat(codes(assessment)).doesNotContain("PSBI_YOUNG_INFANT");
    }

    @Test
    void generalDangerSignsApplyFromTwoMonthsToFiveYears() {
        var toddler = service.evaluate(700, facts("vomitsEverything", "PRESENT"));
        assertThat(codes(toddler)).contains("IMNCI_GENERAL_DANGER_SIGNS");

        // Below two months the young-infant pathway covers the child instead.
        var infant = service.evaluate(30, facts("vomitsEverything", "PRESENT"));
        assertThat(codes(infant)).doesNotContain("IMNCI_GENERAL_DANGER_SIGNS");
    }

    @Test
    void anAirwayEmergencyCannotBeDismissed() {
        var assessment = service.evaluate(400, facts("stridorAtRest", "PRESENT"));

        var alert = assessment.alerts().stream()
                .filter(a -> a.code().equals("ETAT_AIRWAY_COMPROMISE")).findFirst().orElseThrow();
        assertThat(alert.interruptive()).isTrue();
        assertThat(alert.overrideAllowed()).isFalse();
        assertThat(alert.severity()).isEqualTo("CRITICAL");
    }

    @Test
    void anAlertNamesTheFindingsThatActuallyTriggeredIt() {
        var assessment = service.evaluate(400, facts(
                "capillaryRefillSeconds", 4,
                "coldHands", "PRESENT",
                "weakFastPulse", "PRESENT"));

        var shock = assessment.alerts().stream()
                .filter(a -> a.code().equals("ETAT_SHOCK")).findFirst().orElseThrow();
        assertThat(shock.triggeringFindings())
                .contains("capillaryRefillSeconds", "coldHands", "weakFastPulse");
        assertThat(shock.requiredAction()).contains("fluid");
    }

    @Test
    void anAssessmentThatFoundNothingButLookedAtLittleIsMarkedIncomplete() {
        // The most dangerous possible output would be a confident all-clear here.
        var assessment = service.evaluate(400, facts("vomitsEverything", "ABSENT"));

        assertThat(assessment.alerts()).isEmpty();
        assertThat(assessment.incomplete()).isTrue();
        assertThat(assessment.notAssessed()).contains("convulsions", "lethargicOrUnconscious");
    }

    @Test
    void aFullyAssessedWellChildIsNotFlaggedAsIncomplete() {
        var assessment = service.evaluate(400, facts(
                "airwayObstruction", "ABSENT",
                "stridorAtRest", "ABSENT",
                "centralCyanosis", "ABSENT",
                "severeChestIndrawing", "ABSENT",
                "grunting", "ABSENT",
                "capillaryRefillSeconds", 1,
                "coldHands", "ABSENT",
                "weakFastPulse", "ABSENT",
                "consciousness", "ALERT",
                "convulsingNow", "ABSENT",
                "diarrhoea", "ABSENT",
                "sunkenEyes", "ABSENT",
                "skinPinchSlow", "ABSENT",
                "unableToDrinkOrBreastfeed", "ABSENT",
                "vomitsEverything", "ABSENT",
                "convulsions", "ABSENT",
                "lethargicOrUnconscious", "ABSENT",
                "bloodGlucoseMmol", 5.2,
                "muacCm", 14.0,
                "bilateralPittingOedema", "ABSENT",
                "appetiteTest", "PASSED",
                "vitals", Map.of("oxygenSaturation", 98, "temperature", 36.8)));

        assertThat(assessment.alerts()).isEmpty();
        assertThat(assessment.notAssessed()).isEmpty();
        assertThat(assessment.incomplete()).isFalse();
    }

    @Test
    void withoutAnAgeNothingIsEvaluatedAndTheReasonIsGiven() {
        // Danger signs differ by age; applying the wrong set is worse than applying none.
        var assessment = service.evaluate(null, facts("stridorAtRest", "PRESENT"));

        assertThat(assessment.assessed()).isFalse();
        assertThat(assessment.alerts()).isEmpty();
        assertThat(assessment.reason()).contains("age");
    }

    @Test
    void hypoglycaemiaIsCaughtAtAnyPaediatricAge() {
        assertThat(codes(service.evaluate(10, facts("bloodGlucoseMmol", 1.9))))
                .contains("PAEDIATRIC_HYPOGLYCAEMIA");
        assertThat(codes(service.evaluate(3000, facts("bloodGlucoseMmol", 1.9))))
                .contains("PAEDIATRIC_HYPOGLYCAEMIA");
        assertThat(codes(service.evaluate(3000, facts("bloodGlucoseMmol", 5.5))))
                .doesNotContain("PAEDIATRIC_HYPOGLYCAEMIA");
    }

    @Test
    void severeMalnutritionOnlyBecomesAnAdmissionWhenAComplicationIsPresent() {
        var uncomplicated = service.evaluate(500, facts(
                "muacCm", 11.0,
                "bilateralPittingOedema", "ABSENT",
                "appetiteTest", "PASSED",
                "lethargicOrUnconscious", "ABSENT",
                "vitals", Map.of("temperature", 36.5)));
        assertThat(codes(uncomplicated)).doesNotContain("SEVERE_ACUTE_MALNUTRITION_WITH_DANGER");

        var complicated = service.evaluate(500, facts(
                "bilateralPittingOedema", "PRESENT",
                "appetiteTest", "FAILED"));
        assertThat(codes(complicated)).contains("SEVERE_ACUTE_MALNUTRITION_WITH_DANGER");
    }

    @Test
    void everyAlertCarriesItsSourceVersionAndApprovalStatus() {
        var assessment = service.evaluate(10, facts("feeding", "NONE"));

        assertThat(assessment.alerts()).isNotEmpty();
        assessment.alerts().forEach(alert -> {
            assertThat(alert.sourceRefs()).as("%s must cite a source", alert.code()).isNotEmpty();
            assertThat(alert.contentVersion()).isNotBlank();
            // Nothing in this pack is ratified yet, and the response must say so.
            assertThat(alert.approvalStatus()).isEqualTo("ENGINEERING_SEED");
            assertThat(alert.requiredAction()).isNotBlank();
        });
    }

    @Test
    void theResponseMapIsShapedForTheClientAndFlagsIncompleteness() {
        var map = service.evaluate(400, facts("vomitsEverything", "PRESENT")).toMap();

        assertThat(map).containsKeys("assessed", "emergency", "referral_required", "incomplete",
                "alerts", "not_assessed", "rules_applied", "content_version");
        assertThat(map.get("emergency")).isEqualTo(true);
    }

    private static List<String> codes(DangerSignEvaluationService.DangerSignAssessment assessment) {
        return assessment.alerts().stream().map(DangerSignEvaluationService.DangerSignAlert::code).toList();
    }

    private static Map<String, Object> facts(Object... keyValues) {
        Map<String, Object> facts = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            facts.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return facts;
    }
}
