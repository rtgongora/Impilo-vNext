package zw.gov.mohcc.impilo.reproductive.history;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.reproductive.stage.BirthMaturityBand;
import zw.gov.mohcc.impilo.reproductive.stage.PostpartumCalculator;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GravidityParityCalculatorTest {

    private final LossThresholdPolicy policy = LossThresholdPolicy.engineeringSeed();

    @Test
    void twinsAreOneParityAndTwoLivingChildren() {
        // The error this exists to prevent. Counting babies instead of pregnancies inflates parity,
        // and parity drives risk stratification — grand multiparity is a haemorrhage risk factor.
        var result = GravidityParityCalculator.derive(List.of(
                PregnancyHistoryEntry.liveBirth(LocalDate.of(2024, 3, 1), 273, 2, 2)),
                policy, false);

        assertEquals(1, result.gravida());
        assertEquals(1, result.para());
        assertEquals(2, result.living());
        assertEquals("G1 P1 A0", result.gpaDisplay());
    }

    @Test
    void aCurrentPregnancyCountsTowardGravidityAndNotParity() {
        var result = GravidityParityCalculator.derive(List.of(
                PregnancyHistoryEntry.liveBirth(LocalDate.of(2024, 3, 1), 273, 1, 1)),
                policy, true);

        assertEquals(2, result.gravida());
        assertEquals(1, result.para());
    }

    @Test
    void anUncountableHistoryReportsWhatItCouldNotCount_ratherThanReadingAsNullipara() {
        // "G? P?" prompts someone to ask. "G0 P0" reads as an answered question, and it removes a
        // risk factor rather than reporting an unknown.
        var result = GravidityParityCalculator.derive(List.of(
                new PregnancyHistoryEntry(null, null, null, null, null, null, LocalDate.of(2020, 1, 1))),
                policy, false);

        assertNull(result.para());
        assertFalse(result.complete());
        assertTrue(result.needsVerification());
        assertEquals(1, result.entriesUncountable());
        assertTrue(result.gpaDisplay().contains("P?"));
        assertFalse(result.grandMultipara(), "an unknown parity must not raise a risk flag");
    }

    @Test
    void aLiveBirthOfUnknownGestationIsNotBinnedIntoTerm() {
        // Quietly calling it term erases a previous-preterm-birth risk factor, which is one of the
        // strongest predictors of another preterm birth.
        var result = GravidityParityCalculator.derive(List.of(
                new PregnancyHistoryEntry(null, PregnancyOutcome.LIVE_BIRTH, null, 1, 1, 1,
                        LocalDate.of(2023, 5, 1))),
                policy, false);

        assertEquals(1, result.para());
        assertNull(result.term());
        assertNull(result.preterm());
        assertFalse(result.complete());
        assertTrue(result.uncountableReasons().get(0).contains("term or preterm"));
    }

    @Test
    void aLiveBirthFollowedByANeonatalDeathIsAParityWithNoSurvivingChild() {
        var result = GravidityParityCalculator.derive(List.of(
                PregnancyHistoryEntry.liveBirth(LocalDate.of(2022, 2, 1), 280, 1, 0)),
                policy, false);

        assertEquals(1, result.para());
        assertEquals(0, result.living());
    }

    @Test
    void anEarlyMiscarriageIsALossAndALateStillbirthIsABirth() {
        var result = GravidityParityCalculator.derive(List.of(
                PregnancyHistoryEntry.miscarriage(LocalDate.of(2021, 1, 1), 8 * 7),
                new PregnancyHistoryEntry(null, PregnancyOutcome.STILLBIRTH, 32 * 7, 1, 0, 0,
                        LocalDate.of(2022, 1, 1))),
                policy, false);

        assertEquals(2, result.gravida());
        assertEquals(1, result.para(), "a stillbirth at 32 weeks is a birth");
        assertEquals(1, result.abortus());
        assertEquals(1, result.preterm());
    }

    @Test
    void grandMultiparityIsFlaggedOnlyWhenParityIsActuallyKnown() {
        var entries = new java.util.ArrayList<PregnancyHistoryEntry>();
        for (int i = 0; i < 5; i++) {
            entries.add(PregnancyHistoryEntry.liveBirth(LocalDate.of(2015 + i, 1, 1), 273, 1, 1));
        }
        assertTrue(GravidityParityCalculator.derive(entries, policy, false).grandMultipara());
        assertFalse(GravidityParityCalculator.derive(List.of(), policy, false).grandMultipara());
    }

    @Test
    void aWomansOwnAccountIsSurfacedAsADiscrepancyRatherThanOverwritten() {
        // She is usually the only person who was present for all of them. Pregnancies cared for
        // elsewhere are the usual explanation, and silently reconciling loses that.
        var derived = GravidityParityCalculator.derive(List.of(
                PregnancyHistoryEntry.liveBirth(LocalDate.of(2024, 3, 1), 273, 1, 1),
                PregnancyHistoryEntry.liveBirth(LocalDate.of(2022, 3, 1), 273, 1, 1)),
                policy, false);

        var reconciliation = GravidityParityCalculator.reconcile(derived, 5, 4);

        assertFalse(reconciliation.agrees());
        assertEquals(2, reconciliation.discrepancies().size());
        assertTrue(reconciliation.discrepancies().get(0).contains("elsewhere"));
    }

    @Test
    void maturityBandsSplitTermRatherThanCollapsingIt() {
        // Early term carries worse neonatal outcomes than full term, which is why elective delivery
        // before 39 weeks is discouraged. A model that collapses them cannot express that.
        assertEquals(BirthMaturityBand.EARLY_TERM, BirthMaturityBand.ofGestationalAgeDays(37 * 7));
        assertEquals(BirthMaturityBand.FULL_TERM, BirthMaturityBand.ofGestationalAgeDays(39 * 7));
        assertEquals(BirthMaturityBand.POST_TERM, BirthMaturityBand.ofGestationalAgeDays(42 * 7));
        assertTrue(BirthMaturityBand.ofGestationalAgeDays(31 * 7).preterm());
    }

    @Test
    void unknownGestationYieldsNullMaturity_notTerm() {
        assertNull(BirthMaturityBand.ofGestationalAgeDays(null));
        assertNull(BirthMaturityBand.preterm(null),
                "'we do not know how mature this baby is' and 'this baby is term' lead to "
                        + "completely different neonatal care");
    }

    @Test
    void haemorrhageTimingSeparatesPrimaryFromSecondary() {
        // Different causes, different management: primary is usually atony, trauma or retained
        // tissue; secondary is usually retained products or endometritis.
        OffsetDateTime birth = OffsetDateTime.of(2026, 3, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(PostpartumCalculator.PphTiming.PRIMARY,
                PostpartumCalculator.classifyHaemorrhage(birth, birth.plusHours(2)));
        assertEquals(PostpartumCalculator.PphTiming.SECONDARY,
                PostpartumCalculator.classifyHaemorrhage(birth, birth.plusDays(10)));
        assertEquals(PostpartumCalculator.PphTiming.OUTSIDE_WINDOW,
                PostpartumCalculator.classifyHaemorrhage(birth, birth.plusDays(100)));
        assertNull(PostpartumCalculator.classifyHaemorrhage(birth, null));
    }

    @Test
    void maternalDeathWindowsAreNullWhenTheDayIsUnknown() {
        // A death that cannot be placed in time must be reviewed, not excluded by default.
        assertNull(PostpartumCalculator.withinMaternalDeathWindow(null));
        assertTrue(PostpartumCalculator.withinMaternalDeathWindow(42));
        assertFalse(PostpartumCalculator.withinMaternalDeathWindow(43));
        assertTrue(PostpartumCalculator.withinLateMaternalDeathWindow(200));
    }

    @Test
    void postpartumBandsCoverTheFirstYearWithoutGaps() {
        for (int day = 0; day <= 364; day++) {
            assertNotNull(PostpartumCalculator.PostpartumBand.ofDay(day),
                    "no postnatal band covers day " + day);
        }
        assertNull(PostpartumCalculator.PostpartumBand.ofDay(null));
    }
}
