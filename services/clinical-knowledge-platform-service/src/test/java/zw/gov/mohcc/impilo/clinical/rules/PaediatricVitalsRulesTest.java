package zw.gov.mohcc.impilo.clinical.rules;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vital-sign alerts must be read against the patient's age.
 *
 * <p>Adult thresholds applied to a child fail in both directions at once: a tachycardia
 * alert at 120 bpm fires on nearly every well infant, and a bradycardia alert at 50 bpm
 * stays silent for a neonate at 80 bpm — a peri-arrest finding. These tests pin both.</p>
 */
class PaediatricVitalsRulesTest {

    private final ClinicalRulesEngine engine = new ClinicalRulesEngine(3);

    @Test
    void aWellInfantsNormalHeartRateNoLongerRaisesATachycardiaAlert() {
        // 150 bpm in a three-month-old is ordinary. Under adult thresholds this fired every time.
        List<String> codes = codes(context(0.25, 90, vitals(null, null, 150.0, 37.0)));

        assertThat(codes).doesNotContain("VITALS_TACHYCARDIA");
    }

    @Test
    void theSameHeartRateInATeenagerStillRaisesTachycardia() {
        List<String> codes = codes(context(15.0, 15 * 365, vitals(null, null, 150.0, 37.0)));

        assertThat(codes).contains("VITALS_TACHYCARDIA");
    }

    @Test
    void aGenuinelyTachycardicInfantIsStillCaught() {
        List<String> codes = codes(context(0.25, 90, vitals(null, null, 195.0, 37.0)));

        assertThat(codes).contains("VITALS_TACHYCARDIA");
    }

    @Test
    void aBradycardicNeonateIsNowCaughtAndTreatedAsCritical() {
        // 80 bpm in a neonate is peri-arrest. The adult threshold of 50 never fired.
        List<RuleAlert> alerts = engine.evaluate(context(0.02, 7, vitals(null, null, 80.0, 37.0)));

        RuleAlert bradycardia = alerts.stream()
                .filter(a -> a.code().equals("VITALS_BRADYCARDIA"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("bradycardia in a neonate must be flagged"));

        assertThat(bradycardia.severity()).isEqualTo("CRITICAL");
        assertThat(bradycardia.interruptive()).isTrue();
        assertThat(bradycardia.explanation()).contains("hypoxic");
    }

    @Test
    void anAdultAtEightyBeatsPerMinuteIsNotBradycardic() {
        assertThat(codes(context(40.0, 40 * 365, vitals(null, null, 80.0, 37.0))))
                .doesNotContain("VITALS_BRADYCARDIA");
    }

    @Test
    void hypotensionInAChildIsFlaggedAsDecompensatedShockAndCannotBeOverridden() {
        // A systolic of 65 in a three-year-old is decompensated shock; the adult rule had no
        // hypotension threshold at all.
        List<RuleAlert> alerts = engine.evaluate(context(3.0, 3 * 365, vitals(65.0, 40.0, 160.0, 37.0)));

        RuleAlert hypotension = alerts.stream()
                .filter(a -> a.code().equals("VITALS_PAEDIATRIC_HYPOTENSION"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("paediatric hypotension must be flagged"));

        assertThat(hypotension.severity()).isEqualTo("CRITICAL");
        assertThat(hypotension.overrideAllowed()).isFalse();
        assertThat(hypotension.explanation()).contains("late sign");
    }

    @Test
    void hypertensiveCrisisUsesTheAgeAppropriateThreshold() {
        // 135 systolic is a crisis in a four-year-old and unremarkable in an adult.
        assertThat(codes(context(4.0, 4 * 365, vitals(135.0, 85.0, 110.0, 37.0))))
                .contains("VITALS_HYPERTENSIVE_CRISIS");
        assertThat(codes(context(40.0, 40 * 365, vitals(135.0, 85.0, 80.0, 37.0))))
                .doesNotContain("VITALS_HYPERTENSIVE_CRISIS");
    }

    @Test
    void hypothermiaInAYoungChildIsADangerSignRatherThanAColdRoom() {
        List<RuleAlert> alerts = engine.evaluate(context(0.1, 40, vitals(null, null, 140.0, 35.0)));

        RuleAlert hypothermia = alerts.stream()
                .filter(a -> a.code().equals("VITALS_PAEDIATRIC_HYPOTHERMIA"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("hypothermia in an infant must be flagged"));

        assertThat(hypothermia.interruptive()).isTrue();
        assertThat(hypothermia.message()).contains("serious infection");
    }

    @Test
    void anAdultAtThirtyFiveDegreesDoesNotRaiseThePaediatricHypothermiaAlert() {
        assertThat(codes(context(40.0, 40 * 365, vitals(null, null, 70.0, 35.0))))
                .doesNotContain("VITALS_PAEDIATRIC_HYPOTHERMIA");
    }

    @Test
    void theThirteenthBirthdayIsWhereAdultThresholdsTakeOver() {
        // The paediatric-only alerts are the visible boundary: the oldest paediatric band
        // shares the adult heart-rate cut-off, so tachycardia alone cannot show the switch.
        assertThat(codes(context(null, 13 * 365 - 1, vitals(85.0, 60.0, 100.0, 35.0))))
                .contains("VITALS_PAEDIATRIC_HYPOTENSION", "VITALS_PAEDIATRIC_HYPOTHERMIA");
        assertThat(codes(context(null, 13 * 365, vitals(85.0, 60.0, 100.0, 35.0))))
                .doesNotContain("VITALS_PAEDIATRIC_HYPOTENSION", "VITALS_PAEDIATRIC_HYPOTHERMIA");
    }

    @Test
    void aTwelveYearOldAtOneHundredAndThirtyIsStillTachycardic() {
        // The oldest paediatric band shares the adult cut-off, which is clinically right —
        // age-banding is not the same as being more permissive at every age.
        assertThat(codes(context(12.0, 12 * 365, vitals(null, null, 130.0, 37.0))))
                .contains("VITALS_TACHYCARDIA");
    }

    @Test
    void withAgeUnknownAdultThresholdsApplyAndTheAlertSaysSo() {
        // Guessing that an unknown-age patient is a child would be worse; the alert is explicit
        // about which thresholds were used so a clinician can discount it.
        List<RuleAlert> alerts = engine.evaluate(context(null, null, vitals(null, null, 130.0, 37.0)));

        RuleAlert tachycardia = alerts.stream()
                .filter(a -> a.code().equals("VITALS_TACHYCARDIA"))
                .findFirst()
                .orElseThrow();
        assertThat(tachycardia.explanation()).contains("age unknown");
    }

    @Test
    void anAlertNamesTheAgeBandItsThresholdCameFrom() {
        List<RuleAlert> alerts = engine.evaluate(context(0.25, 90, vitals(null, null, 195.0, 37.0)));

        assertThat(alerts.stream()
                .filter(a -> a.code().equals("VITALS_TACHYCARDIA"))
                .findFirst().orElseThrow().explanation())
                .contains("INFANT_1_11M");
    }

    @Test
    void hypoxaemiaAndFeverKeepASingleThresholdAcrossAges() {
        assertThat(codes(context(0.25, 90, vitals(null, null, 140.0, 39.0, 88.0))))
                .contains("VITALS_HYPOXAEMIA", "VITALS_FEVER");
        assertThat(codes(context(40.0, 40 * 365, vitals(null, null, 80.0, 39.0, 88.0))))
                .contains("VITALS_HYPOXAEMIA", "VITALS_FEVER");
    }

    // --- helpers ---------------------------------------------------------------------

    private List<String> codes(ClinicalEvaluationContext ctx) {
        return engine.evaluate(ctx).stream().map(RuleAlert::code).toList();
    }

    private static ClinicalEvaluationContext.Vitals vitals(Double systolic, Double diastolic,
                                                           Double heartRate, Double temperature) {
        return vitals(systolic, diastolic, heartRate, temperature, null);
    }

    private static ClinicalEvaluationContext.Vitals vitals(Double systolic, Double diastolic, Double heartRate,
                                                           Double temperature, Double spo2) {
        return new ClinicalEvaluationContext.Vitals(systolic, diastolic, heartRate, temperature, null, spo2);
    }

    private static ClinicalEvaluationContext context(Double ageYears, Integer ageDays,
                                                     ClinicalEvaluationContext.Vitals vitals) {
        return new ClinicalEvaluationContext(
                ageYears, ageDays, null, "C",
                List.of(), List.of(), null, null, null, vitals, List.of());
    }
}
