package zw.gov.mohcc.impilo.paediatrics.age;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The correction is measured back to full term, not back to the preterm threshold.
 *
 * <p>These are separate constants because they answer separate questions, and this suite tests
 * them separately. Whether a baby is corrected at all is decided at 37 weeks; how far they are
 * corrected is measured to 40. A single constant serving both — which is what this class used
 * to assert — under-corrects every preterm child by three weeks and so reads their growth as
 * slightly worse than it is.</p>
 */
class CorrectedAgeTest {

    @Test
    void theThresholdAndTheTargetAreDifferentWeeks() {
        // The defect this suite guards against is these two collapsing back into one value.
        // 37 decides whether to correct; 40 decides how far. Neither substitutes for the
        // other, and a change making them equal is the bug returning, not a simplification.
        assertEquals(37, CorrectedAge.PRETERM_THRESHOLD_WEEKS);
        assertEquals(40, CorrectedAge.TERM_CORRECTION_TARGET_WEEKS);
        assertNotEquals(CorrectedAge.PRETERM_THRESHOLD_WEEKS, CorrectedAge.TERM_CORRECTION_TARGET_WEEKS);
    }

    @Test
    void babyBornAtThirtyTwoWeeksIsCorrectedByEightWeeks() {
        // Born eight weeks short of term (40 - 32), so at 120 days chronological the child is
        // developmentally and nutritionally 64 days old. Correcting to the 37-week threshold
        // instead would give 85 and read this baby three weeks older than they are.
        assertEquals(120 - 56, CorrectedAge.correctedAgeDays(120, 32));
    }

    @Test
    void aLatePretermBabyIsStillCorrected() {
        // 36 weeks is preterm by one week but four weeks short of term, so the correction is
        // four weeks. Under the old single constant this baby was corrected by seven days.
        assertEquals(120 - 28, CorrectedAge.correctedAgeDays(120, 36));
        assertTrue(CorrectedAge.isPreterm(36));
    }

    @Test
    void termBabiesAreNotCorrectedEvenThoughTheyAreShortOfFortyWeeks() {
        // A baby born at 37, 38 or 39 weeks is short of full term but is not preterm, so no
        // correction applies. The step at the threshold — 36 weeks corrected by four weeks,
        // 37 weeks not at all — is the convention as written, not an artefact of this code.
        assertEquals(120, CorrectedAge.correctedAgeDays(120, 37));
        assertEquals(120, CorrectedAge.correctedAgeDays(120, 39));
        assertEquals(120, CorrectedAge.correctedAgeDays(120, 40));
        assertFalse(CorrectedAge.correctionApplied(120, 39));
    }

    @Test
    void theFentonHandoverLandsAtTenWeeksCorrected() {
        // The preterm chart is published to 49 completed postmenstrual weeks and the child
        // moves to the WHO tables at 50. For a baby born at 30 weeks that is 140 days lived,
        // which the convention puts at exactly 10 weeks corrected. This is the arithmetic the
        // handover is designed around, so getting it wrong moves a child's z-score at exactly
        // the point the standard changes underneath them.
        assertEquals(70, CorrectedAge.correctedAgeDays(140, 30));
    }

    @Test
    void unknownGestationalAgeLeavesTheChronologicalAgeUntouched() {
        assertEquals(120, CorrectedAge.correctedAgeDays(120, null));
        assertFalse(CorrectedAge.isPreterm(null));
    }

    @Test
    void correctionStopsOnceTheChildIsTwoYearsCorrected() {
        // Well past the window: correction would no longer be clinically meaningful.
        int chronological = 3 * 365;
        assertEquals(chronological, CorrectedAge.correctedAgeDays(chronological, 30));
        assertFalse(CorrectedAge.correctionApplied(chronological, 30));
    }

    @Test
    void correctedAgeNeverGoesBelowZero() {
        // A 28-week baby three days old is still twelve weeks short of term. There is no such
        // thing as a negative age to score against, so the floor is zero — and a preterm baby
        // this young is read on the preterm chart at postmenstrual age, never on this value.
        assertEquals(0, CorrectedAge.correctedAgeDays(3, 28));
    }

    @Test
    void correctionIsFlaggedWhenItActuallyChangesTheAge() {
        assertTrue(CorrectedAge.correctionApplied(120, 32));
    }

    @Test
    void unknownChronologicalAgeCannotBeCorrected() {
        assertNull(CorrectedAge.correctedAgeDays(null, 32));
    }
}
