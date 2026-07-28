package zw.gov.mohcc.impilo.medicine.burden;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Treatment burden ranks who a clinic should look at first. The clinical risk in a ranking is
 * silent: nobody notices the patient who should have been at the top and was not. Most of these
 * tests are therefore about the inputs that would push exactly the wrong person down the list.
 */
class TreatmentBurdenScoreTest {

    private static final TreatmentBurdenScore.BurdenThresholds THRESHOLDS =
            new TreatmentBurdenScore.BurdenThresholds(
                    List.of(5, 10, 15),
                    List.of(4, 8, 12),
                    List.of(2, 4, 6));

    @Test
    void eachCountScoresAPointForEveryCutPointItReaches() {
        // The worked example. Eleven medicines clears 5 and 10 but not 15, nine attendances clear
        // 4 and 8 but not 12, five conditions clear 2 and 4 but not 6 — six points out of a
        // possible nine, which is 0.667 on the normalised scale.
        var burden = TreatmentBurdenScore.score(11, 9, 5, THRESHOLDS);

        assertThat(burden.computed()).isTrue();
        assertThat(burden.medicineLevel()).isEqualTo(2);
        assertThat(burden.attendanceLevel()).isEqualTo(2);
        assertThat(burden.longTermConditionLevel()).isEqualTo(2);
        assertThat(burden.totalPoints()).isEqualTo(6);
        assertThat(burden.maxPoints()).isEqualTo(9);
        assertThat(burden.normalisedIndex()).isEqualByComparingTo("0.667");
        assertThat(burden.contentVersion()).isEqualTo(TreatmentBurdenScore.CONTENT_VERSION);
    }

    @Test
    void aPatientOnNothingScoresZeroAndAPatientAtEveryCeilingScoresTheMaximum() {
        // The two ends of the scale, so the normalised index is anchored at 0.000 and 1.000 and a
        // caller can compare across facilities using different cut-point sets.
        var none = TreatmentBurdenScore.score(0, 0, 0, THRESHOLDS);
        var everything = TreatmentBurdenScore.score(20, 20, 10, THRESHOLDS);

        assertThat(none.totalPoints()).isZero();
        assertThat(none.normalisedIndex()).isEqualByComparingTo("0.000");
        assertThat(everything.totalPoints()).isEqualTo(9);
        assertThat(everything.normalisedIndex()).isEqualByComparingTo("1.000");
    }

    @Test
    void aCountExactlyOnACutPointScoresIt() {
        // Cut-points are inclusive: "five or more medicines" is what a ministry means when it
        // writes 5. An exclusive comparison would move every patient sitting exactly on a boundary
        // down a level, and boundaries are where most patients sit.
        var burden = TreatmentBurdenScore.score(5, 4, 2, THRESHOLDS);

        assertThat(burden.medicineLevel()).isEqualTo(1);
        assertThat(burden.attendanceLevel()).isEqualTo(1);
        assertThat(burden.longTermConditionLevel()).isEqualTo(1);
    }

    @Test
    void noThresholdIsHardcodedAnywhereInTheClass() {
        // The entry criterion for this library: arithmetic here, policy in governed content. If a
        // default cut-point ever appears as a constant, a ministry can no longer change where the
        // boundary sits without a code release — and the boundary is the entire decision.
        assertThat(Arrays.stream(TreatmentBurdenScore.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .allMatch(name -> name.startsWith("MAX_PLAUSIBLE") || name.equals("CONTENT_VERSION")
                        || name.startsWith("$") || name.startsWith("__"));
    }

    @Test
    void aMissingCountIsNotACountOfZero() {
        // The ranking failure. A patient whose medication list has never been reconciled scores
        // zero medicines, sinks to the bottom of the burden list, and is exactly the patient who
        // needed reviewing. An absent count has to stay absent.
        var missingMedicines = TreatmentBurdenScore.score(null, 9, 5, THRESHOLDS);
        var missingAttendances = TreatmentBurdenScore.score(11, null, 5, THRESHOLDS);
        var missingConditions = TreatmentBurdenScore.score(11, 9, null, THRESHOLDS);

        assertThat(missingMedicines.computed()).isFalse();
        assertThat(missingMedicines.totalPoints()).isNull();
        assertThat(missingMedicines.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(missingMedicines.explanation()).contains("medicine count");
        assertThat(missingMedicines.explanation()).contains("least-documented");
        assertThat(missingAttendances.explanation()).contains("attendance count");
        assertThat(missingConditions.explanation()).contains("long-term condition count");
    }

    @Test
    void absentThresholdsAreRefusedRatherThanDefaulted() {
        // There is nothing to fall back to, deliberately. A library-supplied default would be a
        // clinical decision made by whoever wrote it, applied nationally, invisibly.
        var burden = TreatmentBurdenScore.score(11, 9, 5, null);

        assertThat(burden.computed()).isFalse();
        assertThat(burden.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(burden.explanation()).contains("governed content");
    }

    @Test
    void cutPointsInTheWrongOrderAreRefusedRatherThanSorted() {
        // An unsorted list is an authoring error in governed content. Sorting it quietly produces a
        // score computed against thresholds nobody wrote and nobody approved, and the content stays
        // broken because nothing ever complained.
        var burden = TreatmentBurdenScore.score(11, 9, 5,
                new TreatmentBurdenScore.BurdenThresholds(
                        List.of(15, 5, 10), List.of(4, 8, 12), List.of(2, 4, 6)));

        assertThat(burden.computed()).isFalse();
        assertThat(burden.reason()).isEqualTo(RefusalReason.INPUTS_INCONSISTENT);
        assertThat(burden.explanation()).contains("strictly ascending");
    }

    @Test
    void duplicateOrNegativeCutPointsAreRefused() {
        // A duplicated cut-point would score two levels for one boundary, silently doubling that
        // dimension's weight relative to the other two.
        var duplicated = TreatmentBurdenScore.score(11, 9, 5,
                new TreatmentBurdenScore.BurdenThresholds(
                        List.of(5, 5, 10), List.of(4, 8, 12), List.of(2, 4, 6)));
        var negative = TreatmentBurdenScore.score(11, 9, 5,
                new TreatmentBurdenScore.BurdenThresholds(
                        List.of(5, 10), List.of(-1, 8), List.of(2, 4)));

        assertThat(duplicated.reason()).isEqualTo(RefusalReason.INPUTS_INCONSISTENT);
        assertThat(negative.reason()).isEqualTo(RefusalReason.INPUTS_INCONSISTENT);
        assertThat(negative.explanation()).contains("negative");
    }

    @Test
    void anEmptyDimensionIsRefusedBecauseTheCompositeWouldSilentlyIgnoreIt() {
        // A dimension with no cut-points contributes nothing and changes nothing about the output
        // shape, so the score would look normal while measuring two things instead of three.
        var burden = TreatmentBurdenScore.score(11, 9, 5,
                new TreatmentBurdenScore.BurdenThresholds(
                        List.of(5, 10), List.of(), List.of(2, 4)));

        assertThat(burden.computed()).isFalse();
        assertThat(burden.reason()).isEqualTo(RefusalReason.INPUTS_INCONSISTENT);
        assertThat(burden.explanation()).contains("silently ignores");
    }

    @Test
    void negativeCountsAreRefused() {
        var burden = TreatmentBurdenScore.score(-1, 9, 5, THRESHOLDS);

        assertThat(burden.computed()).isFalse();
        assertThat(burden.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(burden.explanation()).contains("negative");
    }

    @Test
    void implausibleCountsAreRefusedAsDataFaults() {
        // A medication list duplicated by a bad merge produces hundreds of medicines and would put
        // an ordinary patient at the top of every burden list, displacing the people who belong
        // there. Each dimension is guarded separately so the message names the faulty one.
        assertThat(TreatmentBurdenScore.score(500, 9, 5, THRESHOLDS).explanation())
                .contains("duplicated medication list");
        assertThat(TreatmentBurdenScore.score(11, 5000, 5, THRESHOLDS).explanation())
                .contains("Check the window");
        assertThat(TreatmentBurdenScore.score(11, 9, 400, THRESHOLDS).explanation())
                .contains("duplicated problem list");
    }

    @Test
    void theResultCarriesNoBand() {
        // Where "high burden" starts is a decision about who gets attention, and it is made in
        // governed content. A band on this record would be that decision, taken here, permanently.
        assertThat(TreatmentBurdenScore.Burden.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("band")
                        || method.getName().toLowerCase().contains("severity")
                        || method.getName().toLowerCase().contains("category"));
    }

    @Test
    void dimensionsCanBeWeightedByGivingThemDifferentNumbersOfCutPoints() {
        // Weighting is itself a policy choice, so it is expressed the same way the thresholds are:
        // by what the caller supplies. Four medicine cut-points against two of everything else
        // makes polypharmacy count for twice as much, without a line of code changing.
        var weighted = new TreatmentBurdenScore.BurdenThresholds(
                List.of(3, 5, 8, 12), List.of(6, 12), List.of(3, 6));
        var burden = TreatmentBurdenScore.score(9, 7, 4, weighted);

        assertThat(burden.maxPoints()).isEqualTo(8);
        assertThat(burden.medicineLevel()).isEqualTo(3);
        assertThat(burden.attendanceLevel()).isEqualTo(1);
        assertThat(burden.longTermConditionLevel()).isEqualTo(1);
        assertThat(burden.totalPoints()).isEqualTo(5);
    }
}
