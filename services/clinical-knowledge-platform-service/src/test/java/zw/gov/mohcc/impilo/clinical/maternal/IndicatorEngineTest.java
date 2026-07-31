package zw.gov.mohcc.impilo.clinical.maternal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static zw.gov.mohcc.impilo.clinical.maternal.IndicatorEngine.CaseClassification.*;
import static zw.gov.mohcc.impilo.clinical.maternal.IndicatorEngine.OutcomeCase.*;

/**
 * Programme indicators, and the one property that decides whether coverage figures can be trusted:
 * cases you could not assess are counted, never dropped.
 */
class IndicatorEngineTest {

    private static List<IndicatorEngine.CaseClassification> cases(int num, int notNum, int indet, int oos) {
        List<IndicatorEngine.CaseClassification> c = new ArrayList<>();
        IntStream.range(0, num).forEach(i -> c.add(NUMERATOR));
        IntStream.range(0, notNum).forEach(i -> c.add(NOT_IN_NUMERATOR));
        IntStream.range(0, indet).forEach(i -> c.add(INDETERMINATE));
        IntStream.range(0, oos).forEach(i -> c.add(OUT_OF_SCOPE));
        return c;
    }

    @Test
    @DisplayName("indeterminate cases stay in the denominator — coverage is not inflated by dropping them")
    void indeterminateStaysInDenominator() {
        // 60 covered, 0 definite-no, 40 indeterminate. Dropping the 40 would report 100%; counting
        // them reports 60%, which is the truth.
        var r = IndicatorEngine.compute("ANC1_COVERAGE", cases(60, 0, 40, 0));

        assertThat(r.denominator()).isEqualTo(100);
        assertThat(r.numerator()).isEqualTo(60);
        assertThat(r.rate()).isEqualTo(0.60);
        assertThat(r.indeterminate()).isEqualTo(40);
        assertThat(r.note()).contains("could not be assessed");
        assertThat(r.note()).contains("not as 60.0% of a complete assessment");
    }

    @Test
    @DisplayName("out-of-scope cases are excluded from both numerator and denominator")
    void outOfScopeExcludedFromBoth() {
        var r = IndicatorEngine.compute("PNC_COVERAGE", cases(10, 5, 0, 20));
        assertThat(r.denominator()).isEqualTo(15);
        assertThat(r.numerator()).isEqualTo(10);
    }

    @Test
    @DisplayName("the numerator is never greater than the denominator")
    void numeratorNeverExceedsDenominator() {
        for (int[] mix : new int[][]{{5, 0, 0, 0}, {3, 2, 1, 4}, {0, 0, 7, 0}, {9, 1, 0, 0}}) {
            var r = IndicatorEngine.compute("X", cases(mix[0], mix[1], mix[2], mix[3]));
            assertThat(r.numerator()).isLessThanOrEqualTo(r.denominator());
        }
    }

    @Test
    @DisplayName("the denominator is exactly numerator + not-in-numerator + indeterminate — nobody is dropped")
    void everyInScopeCaseIsCounted() {
        var r = IndicatorEngine.compute("X", cases(12, 7, 5, 30));
        assertThat(r.denominator()).isEqualTo(12 + 7 + 5);
        assertThat(r.numerator() + (r.denominator() - r.numerator() - r.indeterminate()) + r.indeterminate())
                .isEqualTo(r.denominator());
    }

    @Test
    @DisplayName("a rate over no cases is undefined, not zero")
    void emptyDenominatorIsUndefined() {
        var r = IndicatorEngine.compute("X", cases(0, 0, 0, 15));
        assertThat(r.denominator()).isZero();
        assertThat(r.rate()).isNull();
        assertThat(r.indeterminateRate()).isNull();
        assertThat(r.note()).contains("undefined, not zero");
    }

    @Test
    @DisplayName("a fully-assessed indicator says so")
    void fullyAssessedIsClean() {
        var r = IndicatorEngine.compute("X", cases(80, 20, 0, 0));
        assertThat(r.rate()).isEqualTo(0.80);
        assertThat(r.indeterminate()).isZero();
        assertThat(r.note()).contains("Every in-scope case was assessed");
    }

    @Test
    @DisplayName("null and empty case lists give an undefined rate, never a fabricated one")
    void nullCasesAreUndefined() {
        assertThat(IndicatorEngine.compute("X", null).rate()).isNull();
        assertThat(IndicatorEngine.compute("X", List.of()).rate()).isNull();
    }

    // ── Severe maternal outcomes ───────────────────────────────────────────────────────────────────

    private static List<IndicatorEngine.OutcomeCase> outcomes(int nearMiss, int deaths, int indet,
                                                              int notSevere) {
        List<IndicatorEngine.OutcomeCase> c = new ArrayList<>();
        IntStream.range(0, nearMiss).forEach(i -> c.add(NEAR_MISS));
        IntStream.range(0, deaths).forEach(i -> c.add(MATERNAL_DEATH));
        IntStream.range(0, indet).forEach(i -> c.add(NEAR_MISS_INDETERMINATE));
        IntStream.range(0, notSevere).forEach(i -> c.add(NOT_SEVERE));
        return c;
    }

    @Test
    @DisplayName("the ratio and index are computed the WHO way over confirmed cases")
    void whoDefinitions() {
        // 20 women survived a severe complication, 2 died.
        var r = IndicatorEngine.computeSevereMaternalOutcomes(outcomes(20, 2, 0, 500));

        assertThat(r.severeMaternalOutcomes()).isEqualTo(22);
        assertThat(r.nearMissToDeathRatio()).isEqualTo(10.0);
        // 2 of the 22 severe outcomes died.
        assertThat(r.mortalityIndex()).isCloseTo(2.0 / 22, within(1e-9));
        assertThat(r.note()).contains("higher is worse");
    }

    @Test
    @DisplayName("an unclassifiable survivor never improves the mortality index")
    void indeterminateDoesNotFlatter() {
        var confirmed = IndicatorEngine.computeSevereMaternalOutcomes(outcomes(10, 5, 0, 0));
        var withUnknowns = IndicatorEngine.computeSevereMaternalOutcomes(outcomes(10, 5, 40, 0));

        // Counting the 40 unresolved survivors as near-misses would take the index from 33% to 5.6%,
        // so an unmeasured creatinine would read as better care. The point estimate must not move.
        assertThat(withUnknowns.mortalityIndex()).isEqualTo(confirmed.mortalityIndex());
        assertThat(withUnknowns.nearMissToDeathRatio()).isEqualTo(confirmed.nearMissToDeathRatio());

        // They are not dropped either: they are counted, reported, and priced as a bound.
        assertThat(withUnknowns.indeterminate()).isEqualTo(40);
        assertThat(withUnknowns.mortalityIndexLowerBound()).isLessThan(withUnknowns.mortalityIndex());
        assertThat(withUnknowns.nearMissToDeathRatioUpperBound())
                .isGreaterThan(withUnknowns.nearMissToDeathRatio());
        assertThat(withUnknowns.note()).contains("flatter the service");
        assertThat(withUnknowns.note()).contains("recording problem");
    }

    @Test
    @DisplayName("the bounds bracket the true value and collapse onto it when nothing is unresolved")
    void boundsBracketTheTruth() {
        var r = IndicatorEngine.computeSevereMaternalOutcomes(outcomes(10, 5, 15, 0));
        // Truth lies between "none of the 15 were near-misses" and "all of them were".
        assertThat(r.mortalityIndexLowerBound()).isCloseTo(5.0 / 30, within(1e-9));
        assertThat(r.mortalityIndex()).isCloseTo(5.0 / 15, within(1e-9));

        var complete = IndicatorEngine.computeSevereMaternalOutcomes(outcomes(10, 5, 0, 0));
        assertThat(complete.mortalityIndexLowerBound()).isEqualTo(complete.mortalityIndex());
        assertThat(complete.nearMissToDeathRatioUpperBound()).isEqualTo(complete.nearMissToDeathRatio());
    }

    @Test
    @DisplayName("no deaths is an undefined ratio, not an infinite one and not a perfect score")
    void zeroDeathsIsUndefined() {
        var r = IndicatorEngine.computeSevereMaternalOutcomes(outcomes(8, 0, 0, 100));

        // Dividing by zero deaths would yield Infinity, which serialises and then gets ranked.
        assertThat(r.nearMissToDeathRatio()).isNull();
        assertThat(r.nearMissToDeathRatioUpperBound()).isNull();
        // The index IS defined here: 0 of 8 severe outcomes died.
        assertThat(r.mortalityIndex()).isEqualTo(0.0);
        assertThat(r.note()).contains("not evidence that none will occur");
    }

    @Test
    @DisplayName("no severe outcomes at all leaves both figures undefined, not zero")
    void noSevereOutcomesIsUndefined() {
        var r = IndicatorEngine.computeSevereMaternalOutcomes(outcomes(0, 0, 0, 300));

        assertThat(r.mortalityIndex()).isNull();
        assertThat(r.nearMissToDeathRatio()).isNull();
        assertThat(r.note()).contains("undefined, not zero");
        // The distinction a league table erases: this says nothing about the service.
        assertThat(r.note()).contains("not about the safety of this service");
    }

    @Test
    @DisplayName("every case lands somewhere: near-miss + deaths + indeterminate is the severe cohort")
    void noCaseIsLost() {
        for (int[] mix : new int[][]{{20, 2, 0, 5}, {0, 3, 7, 1}, {10, 5, 15, 0}, {1, 1, 1, 1}}) {
            var r = IndicatorEngine.computeSevereMaternalOutcomes(outcomes(mix[0], mix[1], mix[2], mix[3]));
            assertThat(r.nearMiss() + r.maternalDeaths()).isEqualTo(r.severeMaternalOutcomes());
            assertThat(r.nearMiss()).isEqualTo(mix[0]);
            assertThat(r.maternalDeaths()).isEqualTo(mix[1]);
            assertThat(r.indeterminate()).isEqualTo(mix[2]);
        }
    }

    @Test
    @DisplayName("null and empty outcome lists are undefined, never fabricated")
    void nullOutcomesAreUndefined() {
        assertThat(IndicatorEngine.computeSevereMaternalOutcomes(null).mortalityIndex()).isNull();
        assertThat(IndicatorEngine.computeSevereMaternalOutcomes(List.of()).nearMissToDeathRatio())
                .isNull();
    }
}
