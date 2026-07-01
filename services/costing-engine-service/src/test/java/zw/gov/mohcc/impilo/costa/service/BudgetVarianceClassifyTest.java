package zw.gov.mohcc.impilo.costa.service;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.costa.domain.enums.VarianceClassification;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Transparent variance classification — deterministic threshold table. */
class BudgetVarianceClassifyTest {

    private static VarianceClassification classify(String budgeted, String actual, String elapsed) {
        return BudgetVarianceService.classify(new BigDecimal(budgeted), new BigDecimal(actual), new BigDecimal(elapsed));
    }

    @Test void nothingBudgeted_onTrack() {
        assertEquals(VarianceClassification.ON_TRACK, classify("0", "0", "0.5"));
    }

    @Test void actualExceedsBudget_overspent() {
        assertEquals(VarianceClassification.OVERSPENT, classify("1000", "1100", "0.5"));
    }

    @Test void spendMatchesElapsedPace_onTrack() {
        assertEquals(VarianceClassification.ON_TRACK, classify("1000", "500", "0.5"));
    }

    @Test void modestlyBehindPace_favourable() {
        // pace = 350 - 500 = -150 => -15% (not stale)
        assertEquals(VarianceClassification.FAVOURABLE, classify("1000", "350", "0.5"));
    }

    @Test void wellBehindPace_underspentStale() {
        // pace = 250 - 500 = -250 => -25% and >25% elapsed
        assertEquals(VarianceClassification.UNDERSPENT_STALE, classify("1000", "250", "0.5"));
    }

    @Test void modestlyAheadOfPace_unfavourableMinor() {
        // pace = 600 - 500 = +100 => +10%
        assertEquals(VarianceClassification.UNFAVOURABLE_MINOR, classify("1000", "600", "0.5"));
    }

    @Test void wellAheadOfPace_unfavourableMajor() {
        // pace = 700 - 500 = +200 => +20%
        assertEquals(VarianceClassification.UNFAVOURABLE_MAJOR, classify("1000", "700", "0.5"));
    }
}
