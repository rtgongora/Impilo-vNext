package zw.gov.mohcc.impilo.medicine.thrombosis;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Wells DVT score sets the prior against which a D-dimer or an ultrasound is read. Each test
 * names the harm the assertion stands between a patient and.
 */
class WellsDvtScoreTest {

    @Test
    void aPatientWithNoCriteriaScoresZeroAndIsUnlikely() {
        var result = WellsDvtScore.of(EnumSet.noneOf(WellsDvtScore.Criterion.class), true);

        assertThat(result.computed()).isTrue();
        assertThat(result.score()).isZero();
        assertThat(result.probability()).isEqualTo(WellsDvtScore.Probability.UNLIKELY);
        assertThat(result.contentVersion()).isEqualTo(WellsDvtScore.CONTENT_VERSION);
    }

    @Test
    void threeOnePointCriteriaMakeDvtLikely() {
        var result = WellsDvtScore.of(EnumSet.of(
                WellsDvtScore.Criterion.ACTIVE_CANCER,
                WellsDvtScore.Criterion.ENTIRE_LEG_SWOLLEN,
                WellsDvtScore.Criterion.LOCALISED_TENDERNESS), true);

        assertThat(result.score()).isEqualTo(3);
        assertThat(result.probability()).isEqualTo(WellsDvtScore.Probability.LIKELY);
    }

    @Test
    void theAlternativeDiagnosisItemSubtractsTwoAndCanMoveAPatientOutOfLikely() {
        // The only negative item, and the reason it cannot be skipped. The same three findings put
        // this patient at "likely" until the clinician judges another diagnosis at least as likely,
        // at which point the score is 1 and the pathway changes.
        Set<WellsDvtScore.Criterion> findings = EnumSet.of(
                WellsDvtScore.Criterion.ACTIVE_CANCER,
                WellsDvtScore.Criterion.ENTIRE_LEG_SWOLLEN,
                WellsDvtScore.Criterion.LOCALISED_TENDERNESS);

        var without = WellsDvtScore.of(findings, true);

        Set<WellsDvtScore.Criterion> withAlternative = EnumSet.copyOf(findings);
        withAlternative.add(WellsDvtScore.Criterion.ALTERNATIVE_DIAGNOSIS_AS_LIKELY);
        var with = WellsDvtScore.of(withAlternative, true);

        assertThat(without.probability()).isEqualTo(WellsDvtScore.Probability.LIKELY);
        assertThat(with.score()).isEqualTo(1);
        assertThat(with.probability()).isEqualTo(WellsDvtScore.Probability.UNLIKELY);
    }

    @Test
    void anUnassessedAlternativeDiagnosisIsRefusedRatherThanInflatingTheScoreByTwo() {
        // Leaving the negative item unanswered is not neutral: it is worth two points in the
        // direction that moves patients into "likely" and onto an anticoagulation pathway.
        var result = WellsDvtScore.of(EnumSet.of(WellsDvtScore.Criterion.ACTIVE_CANCER), null);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("subtracts two points");
    }

    @Test
    void anUnrecordedAssessmentIsRefusedRatherThanScoredAsAllNegative() {
        // A null criteria set is "nobody assessed this patient". An empty set is "assessed, nothing
        // found". Collapsing the first into the second reports an unexamined leg as a normal one.
        var result = WellsDvtScore.of(null, true);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
    }

    @Test
    void theScoreCanGoNegativeAndIsNotClampedToZero() {
        // A patient with only an alternative diagnosis scores −2. Clamping to zero would be
        // harmless here but would mask an implementation that had lost the negative weight.
        var result = WellsDvtScore.of(
                EnumSet.of(WellsDvtScore.Criterion.ALTERNATIVE_DIAGNOSIS_AS_LIKELY), true);

        assertThat(result.score()).isEqualTo(-2);
        assertThat(result.probability()).isEqualTo(WellsDvtScore.Probability.UNLIKELY);
    }

    @Test
    void theTwoPointBoundaryFallsOnThePublishedNumber() {
        var one = WellsDvtScore.of(EnumSet.of(WellsDvtScore.Criterion.ACTIVE_CANCER), true);
        var two = WellsDvtScore.of(EnumSet.of(WellsDvtScore.Criterion.ACTIVE_CANCER,
                WellsDvtScore.Criterion.ENTIRE_LEG_SWOLLEN), true);

        assertThat(one.probability()).isEqualTo(WellsDvtScore.Probability.UNLIKELY);
        assertThat(two.probability()).isEqualTo(WellsDvtScore.Probability.LIKELY);
    }

    @Test
    void theCriteriaThatContributedAreCarriedSoAClinicianCanCheckTheTotal() {
        var result = WellsDvtScore.of(EnumSet.of(WellsDvtScore.Criterion.ACTIVE_CANCER,
                WellsDvtScore.Criterion.PREVIOUSLY_DOCUMENTED_DVT), true);

        assertThat(result.criteriaMet()).containsExactlyInAnyOrder(
                WellsDvtScore.Criterion.ACTIVE_CANCER,
                WellsDvtScore.Criterion.PREVIOUSLY_DOCUMENTED_DVT);
    }
}
