package zw.gov.mohcc.impilo.simba.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.simba.persistence.entity.WellnessPlanEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test for deterministic plan-recommendation scoring (pure scorePlan). */
class PlanRecommendationServiceTest {

    private WellnessPlanEntity plan(String category, boolean clinicalCaution) {
        WellnessPlanEntity p = new WellnessPlanEntity();
        p.setCategory(category);
        p.setClinicalCaution(clinicalCaution);
        p.setName(category + " plan");
        p.setPlanCode("PLAN-" + category);
        return p;
    }

    @Test
    void chronicPreventionRisesWithElevatedRisk() {
        int high = PlanRecommendationService.scorePlan(plan("CHRONIC_PREVENTION", true), "HIGH");
        int moderate = PlanRecommendationService.scorePlan(plan("CHRONIC_PREVENTION", true), "MODERATE");
        int low = PlanRecommendationService.scorePlan(plan("CHRONIC_PREVENTION", true), "LOW");
        assertThat(high).isGreaterThan(moderate);
        assertThat(moderate).isGreaterThan(low);
    }

    @Test
    void lifestylePlansLeadWhenNoAssessment() {
        int activity = PlanRecommendationService.scorePlan(plan("ACTIVITY", false), null);
        int chronic = PlanRecommendationService.scorePlan(plan("CHRONIC_PREVENTION", true), null);
        assertThat(activity).isGreaterThan(chronic);
    }

    @Test
    void mentalWellbeingRisesForClinicalReviewBand() {
        int review = PlanRecommendationService.scorePlan(plan("MENTAL_WELLBEING", false), "NEEDS_CLINICAL_REVIEW");
        int low = PlanRecommendationService.scorePlan(plan("MENTAL_WELLBEING", false), "LOW");
        assertThat(review).isGreaterThan(low);
    }

    @Test
    void everyActivePlanIsAtLeastOfferable() {
        assertThat(PlanRecommendationService.scorePlan(plan("WORKPLACE", false), "LOW")).isPositive();
    }
}
