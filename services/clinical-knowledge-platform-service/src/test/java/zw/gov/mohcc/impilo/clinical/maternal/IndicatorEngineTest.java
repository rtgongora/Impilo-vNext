package zw.gov.mohcc.impilo.clinical.maternal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static zw.gov.mohcc.impilo.clinical.maternal.IndicatorEngine.CaseClassification.*;

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
}
