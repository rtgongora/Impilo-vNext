package zw.gov.mohcc.impilo.paediatrics.age;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrectedAgeTest {

    @Test
    void babyBornAtThirtyTwoWeeksIsCorrectedByFiveWeeks() {
        // 4 months chronological (120 days) minus 5 weeks of prematurity.
        assertEquals(120 - 35, CorrectedAge.correctedAgeDays(120, 32));
    }

    @Test
    void termBabiesAreNotCorrected() {
        assertEquals(120, CorrectedAge.correctedAgeDays(120, 40));
        assertEquals(120, CorrectedAge.correctedAgeDays(120, 37));
        assertFalse(CorrectedAge.correctionApplied(120, 39));
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
        // A 28-week baby three days old is still nine weeks short of term.
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
