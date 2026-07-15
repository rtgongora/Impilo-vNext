package zw.gov.mohcc.impilo.simba.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.simba.persistence.entity.RiskRuleEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Table-driven unit test for deterministic risk scoring — the highest-value test in Wave 1.
 * Exercises each band, RANGE/GTE/IN/EQ operators, the red-flag short-circuit, and transparency detail.
 */
class RiskScoringServiceTest {

    private RiskRuleEntity rule(String code, String question, String op,
                                Double low, Double high, String matchValues,
                                String band, double weight, boolean redFlag) {
        RiskRuleEntity r = new RiskRuleEntity();
        r.setRuleCode(code);
        r.setTemplateCode("BASELINE_WELLNESS");
        r.setQuestionCode(question);
        r.setOperator(op);
        r.setThresholdLow(low == null ? null : BigDecimal.valueOf(low));
        r.setThresholdHigh(high == null ? null : BigDecimal.valueOf(high));
        r.setMatchValues(matchValues);
        r.setContributesBand(band);
        r.setWeight(BigDecimal.valueOf(weight));
        r.setRedFlag(redFlag);
        r.setRationale(code + " rationale");
        return r;
    }

    /** Mirrors the V006-seeded BASELINE_WELLNESS rule set. */
    private List<RiskRuleEntity> baselineRules() {
        return List.of(
                rule("BP_SEVERE", "systolic_bp", "GTE", 180.0, null, null, "URGENT_RED_FLAG", 5.0, true),
                rule("BP_HIGH", "systolic_bp", "RANGE", 140.0, 179.999, null, "HIGH", 3.0, false),
                rule("BP_ELEVATED", "systolic_bp", "RANGE", 130.0, 139.999, null, "MODERATE", 1.5, false),
                rule("BMI_OBESE", "bmi", "GTE", 30.0, null, null, "HIGH", 2.0, false),
                rule("BMI_OVERWEIGHT", "bmi", "RANGE", 25.0, 29.999, null, "MODERATE", 1.0, false),
                rule("GLUCOSE_DIABETIC", "fasting_glucose", "GTE", 11.1, null, null, "NEEDS_CLINICAL_REVIEW", 3.0, false),
                rule("GLUCOSE_PREDIABETIC", "fasting_glucose", "RANGE", 6.1, 11.099, null, "MODERATE", 1.5, false),
                rule("TOBACCO_CURRENT", "tobacco_use", "IN", null, null, "DAILY,WEEKLY", "MODERATE", 1.5, false),
                rule("MOOD_SEVERE", "mood_score", "GTE", 20.0, null, null, "NEEDS_CLINICAL_REVIEW", 3.0, false),
                rule("SELF_HARM_FLAG", "self_harm_thoughts", "EQ", null, null, "YES", "URGENT_RED_FLAG", 5.0, true),
                rule("WAIST_HIGH", "waist_cm", "GTE", 102.0, null, null, "MODERATE", 1.0, false));
    }

    @Test
    void allHealthy_isLow() {
        var res = RiskScoringService.evaluate(baselineRules(), Map.of(
                "systolic_bp", 118, "bmi", 22.5, "fasting_glucose", 5.0,
                "tobacco_use", "NEVER", "mood_score", 2, "waist_cm", 84));
        assertThat(res.band()).isEqualTo("LOW");
        assertThat(res.contributions()).isEmpty();
        assertThat(res.score()).isZero();
    }

    @Test
    void overweightOnly_isModerate() {
        var res = RiskScoringService.evaluate(baselineRules(), Map.of("bmi", 27.0));
        assertThat(res.band()).isEqualTo("MODERATE");
        assertThat(res.contributions()).extracting(RiskScoringService.RuleContribution::ruleCode)
                .containsExactly("BMI_OVERWEIGHT");
    }

    @Test
    void stageOneHypertension_isHigh() {
        var res = RiskScoringService.evaluate(baselineRules(), Map.of("systolic_bp", 150));
        assertThat(res.band()).isEqualTo("HIGH");
    }

    @Test
    void highestBandWins_whenMultipleMatch() {
        // overweight (MODERATE) + obese won't both fire; use BMI 31 (HIGH) + elevated BP (MODERATE)
        var res = RiskScoringService.evaluate(baselineRules(), Map.of("bmi", 31.0, "systolic_bp", 135));
        assertThat(res.band()).isEqualTo("HIGH");
        assertThat(res.contributions()).hasSize(2);
        assertThat(res.score()).isEqualTo(2.0 + 1.5);
    }

    @Test
    void diabeticGlucose_needsClinicalReview() {
        var res = RiskScoringService.evaluate(baselineRules(), Map.of("fasting_glucose", 12.0));
        assertThat(res.band()).isEqualTo("NEEDS_CLINICAL_REVIEW");
    }

    @Test
    void hypertensiveCrisis_redFlagShortCircuitsToUrgent() {
        // Severe BP is a red-flag; even paired with lower-band matches it must be URGENT.
        var res = RiskScoringService.evaluate(baselineRules(), Map.of("systolic_bp", 190, "bmi", 27.0));
        assertThat(res.band()).isEqualTo("URGENT_RED_FLAG");
        assertThat(res.contributions()).anyMatch(RiskScoringService.RuleContribution::redFlag);
    }

    @Test
    void selfHarm_yesIsUrgentRedFlag() {
        var res = RiskScoringService.evaluate(baselineRules(), Map.of("self_harm_thoughts", "YES"));
        assertThat(res.band()).isEqualTo("URGENT_RED_FLAG");
        assertThat(RiskScoringService.isUrgent(res.band())).isTrue();
    }

    @Test
    void selfHarm_noDoesNotFlag() {
        var res = RiskScoringService.evaluate(baselineRules(), Map.of("self_harm_thoughts", "NO"));
        assertThat(res.band()).isEqualTo("LOW");
    }

    @Test
    void tobacco_inSetMatches() {
        var res = RiskScoringService.evaluate(baselineRules(), Map.of("tobacco_use", "DAILY"));
        assertThat(res.band()).isEqualTo("MODERATE");
    }

    @Test
    void rangeBoundary_isInclusive() {
        // 140 is the low bound of BP_HIGH RANGE (140-179.999) -> HIGH, not MODERATE elevated.
        var res = RiskScoringService.evaluate(baselineRules(), Map.of("systolic_bp", 140));
        assertThat(res.band()).isEqualTo("HIGH");
    }

    @Test
    void warrantsCareLinkage_atHighAndAbove() {
        assertThat(RiskScoringService.warrantsCareLinkage("LOW")).isFalse();
        assertThat(RiskScoringService.warrantsCareLinkage("MODERATE")).isFalse();
        assertThat(RiskScoringService.warrantsCareLinkage("HIGH")).isTrue();
        assertThat(RiskScoringService.warrantsCareLinkage("NEEDS_CLINICAL_REVIEW")).isTrue();
        assertThat(RiskScoringService.warrantsCareLinkage("URGENT_RED_FLAG")).isTrue();
    }

    @Test
    void detail_capturesPerRuleContributions() {
        var res = RiskScoringService.evaluate(baselineRules(), Map.of("systolic_bp", 150, "bmi", 31.0));
        var detail = RiskScoringService.detailOf(res);
        assertThat(detail.get("band")).isEqualTo("HIGH");
        assertThat((List<?>) detail.get("contributions")).hasSize(2);
    }
}
