package zw.gov.mohcc.impilo.paediatrics.age;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import zw.gov.mohcc.impilo.paediatrics.age.PaediatricAgeRouter.PresentationContext;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaediatricAgeRouterTest {

    @ParameterizedTest(name = "day {0} falls in band {1}")
    @CsvSource({
            "0, NEWBORN_EARLY",
            "7, NEWBORN_EARLY",
            "8, NEWBORN_LATE",
            "28, NEWBORN_LATE",
            "29, INFANT",
            "364, INFANT",
            "365, YOUNG_CHILD",
            "1824, YOUNG_CHILD",
            "1825, SCHOOL_AGE",
            "3649, SCHOOL_AGE",
            "3650, ADOLESCENT_YOUNG",
            "5474, ADOLESCENT_YOUNG",
            "5475, ADOLESCENT_OLDER",
            "6574, ADOLESCENT_OLDER",
            "6575, ADULT_TRANSITION"
    })
    void bandBoundariesAreExact(int ageDays, PaediatricAgeBand expected) {
        assertEquals(expected, PaediatricAgeRouter.band(ageDays));
    }

    @Test
    void everyAgeFallsInExactlyOneBand() {
        for (int ageDays = 0; ageDays <= 7000; ageDays++) {
            int matches = 0;
            for (PaediatricAgeBand band : PaediatricAgeBand.values()) {
                if (band.covers(ageDays)) {
                    matches++;
                }
            }
            assertEquals(1, matches, "age " + ageDays + " days matched " + matches + " bands");
        }
    }

    @Test
    void sickYoungInfantWindowEndsOnDayFiftyNine() {
        assertTrue(PaediatricAgeRouter.isSickYoungInfantAge(59));
        assertFalse(PaediatricAgeRouter.isSickYoungInfantAge(60));
    }

    @Test
    void underFiveWindowEndsAtFiftyNineCompletedMonths() {
        assertTrue(PaediatricAgeRouter.isUnderFive(1824));
        assertFalse(PaediatricAgeRouter.isUnderFive(1825));
    }

    @Test
    void unwellInfantUnderSixtyDaysGoesToTheSickYoungInfantPathway() {
        var routing = PaediatricAgeRouter.route(10, null, PresentationContext.ACUTE);

        assertEquals(PaediatricPathway.SICK_YOUNG_INFANT, routing.pathway());
        assertEquals(PaediatricAgeBand.NEWBORN_LATE, routing.ageBand());
        assertTrue(routing.sickYoungInfantWindow());
        assertNotNull(routing.rationale());
    }

    @Test
    void unwellChildJustOverSixtyDaysGoesToImnciInstead() {
        var routing = PaediatricAgeRouter.route(60, null, PresentationContext.ACUTE);

        assertEquals(PaediatricPathway.IMNCI_UNDER_FIVE, routing.pathway());
        assertFalse(routing.sickYoungInfantWindow());
        assertTrue(routing.underFive());
    }

    @Test
    void routineUnderFiveContactOpensTheWellChildVisit() {
        assertEquals(PaediatricPathway.WELL_CHILD,
                PaediatricAgeRouter.route(400, null, PresentationContext.ROUTINE).pathway());
    }

    @Test
    void deliveryPresentationAlwaysOpensTheBirthEpisode() {
        assertEquals(PaediatricPathway.BIRTH_EPISODE,
                PaediatricAgeRouter.route(0, 39, PresentationContext.BIRTH).pathway());
    }

    @Test
    void schoolAgeIllnessMovesFromClassificationToFullClerking() {
        assertEquals(PaediatricPathway.GENERAL_PAEDIATRIC,
                PaediatricAgeRouter.route(2000, null, PresentationContext.ACUTE).pathway());
    }

    @Test
    void adolescentsGetTheConfidentialPathwayAtBothEnds() {
        assertEquals(PaediatricPathway.ADOLESCENT,
                PaediatricAgeRouter.route(3650, null, PresentationContext.ROUTINE).pathway());
        assertEquals(PaediatricPathway.ADOLESCENT,
                PaediatricAgeRouter.route(6574, null, PresentationContext.ACUTE).pathway());
    }

    @Test
    void eighteenthBirthdayHandsOverToAdultServices() {
        assertEquals(PaediatricPathway.TRANSITION_TO_ADULT,
                PaediatricAgeRouter.route(6575, null, PresentationContext.ROUTINE).pathway());
    }

    @Test
    void unknownAgeRecommendsNothingRatherThanGuessing() {
        var routing = PaediatricAgeRouter.route((Integer) null, null, PresentationContext.ACUTE);

        assertEquals(PaediatricPathway.UNDETERMINED, routing.pathway());
        assertNull(routing.ageBand());
        assertTrue(routing.rationale().contains("Date of birth"));
    }

    @Test
    void pretermRoutingUsesChronologicalAgeButStillReportsCorrectedAge() {
        // 40 days old, born at 30 weeks: corrected age is negative-equivalent, but the
        // infection risk that drives the sick-young-infant pathway follows the calendar.
        var routing = PaediatricAgeRouter.route(40, 30, PresentationContext.ACUTE);

        assertEquals(PaediatricPathway.SICK_YOUNG_INFANT, routing.pathway());
        assertTrue(routing.preterm());
        assertEquals(0, routing.correctedAgeDays());
        assertEquals(40, routing.ageDays());
    }

    @Test
    void routesFromDatesOfBirthAsWellAsDayCounts() {
        var routing = PaediatricAgeRouter.route(
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-07-26"), PresentationContext.ACUTE);

        assertEquals(55, routing.ageDays());
        assertEquals(PaediatricPathway.SICK_YOUNG_INFANT, routing.pathway());
    }
}
