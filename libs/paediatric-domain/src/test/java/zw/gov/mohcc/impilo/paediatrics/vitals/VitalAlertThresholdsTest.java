package zw.gov.mohcc.impilo.paediatrics.vitals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VitalAlertThresholdsTest {

    @Test
    void everyAgeFromBirthOnwardsHasABand() {
        // A gap would leave a real patient with no thresholds at all, which is worse than
        // having the wrong ones, because nothing would fire.
        for (int ageDays = 0; ageDays <= 40 * 365; ageDays++) {
            assertNotNull(VitalAlertThresholds.bandFor(null, ageDays), "no band for age " + ageDays + " days");
        }
    }

    @Test
    void bandBoundariesFallWhereTheyAreLabelled() {
        assertEquals("NEONATE_0_28D", VitalAlertThresholds.bandFor(null, 28).id());
        assertEquals("INFANT_1_11M", VitalAlertThresholds.bandFor(null, 29).id());
        assertEquals("INFANT_1_11M", VitalAlertThresholds.bandFor(null, 364).id());
        assertEquals("YOUNG_CHILD_1_4Y", VitalAlertThresholds.bandFor(null, 365).id());
        assertEquals("YOUNG_CHILD_1_4Y", VitalAlertThresholds.bandFor(null, 1824).id());
        assertEquals("SCHOOL_AGE_5_11Y", VitalAlertThresholds.bandFor(null, 1825).id());
        assertEquals("SCHOOL_AGE_5_11Y", VitalAlertThresholds.bandFor(null, 4379).id());
        assertEquals("ADOLESCENT_12Y", VitalAlertThresholds.bandFor(null, 4380).id());
    }

    @Test
    void unknownAgeYieldsNoBandSoTheCallerCannotGuess() {
        assertNull(VitalAlertThresholds.bandFor(null, null));
        assertNull(VitalAlertThresholds.bandFor(null, -1));
    }

    @Test
    void adultThresholdsApplyFromThirteenYears() {
        assertTrue(VitalAlertThresholds.adultThresholdsApply(null, 13 * 365));
        assertFalse(VitalAlertThresholds.adultThresholdsApply(null, 13 * 365 - 1));
        assertTrue(VitalAlertThresholds.adultThresholdsApply(40.0, null));
        assertFalse(VitalAlertThresholds.adultThresholdsApply(2.0, null));
        assertFalse(VitalAlertThresholds.adultThresholdsApply(null, null));
    }

    @Test
    void ageInYearsIsAcceptedWhenDaysAreNotKnown() {
        assertEquals("YOUNG_CHILD_1_4Y", VitalAlertThresholds.bandFor(3.0, null).id());
    }

    @Test
    void tachycardiaThresholdsFallAsChildrenGrow() {
        int neonate = VitalAlertThresholds.bandFor(null, 10).tachycardiaAbove();
        int schoolAge = VitalAlertThresholds.bandFor(null, 2000).tachycardiaAbove();
        int adolescent = VitalAlertThresholds.bandFor(null, 5000).tachycardiaAbove();

        assertTrue(neonate > schoolAge, "a neonate tolerates a faster rate than a school-age child");
        assertTrue(schoolAge > adolescent, "an adolescent's threshold approaches the adult one");
    }

    @Test
    void bradycardiaThresholdsAlsoFallWithAge() {
        assertTrue(VitalAlertThresholds.bandFor(null, 10).bradycardiaBelow()
                > VitalAlertThresholds.bandFor(null, 5000).bradycardiaBelow());
    }
}
