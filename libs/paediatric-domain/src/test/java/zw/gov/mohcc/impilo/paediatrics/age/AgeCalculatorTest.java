package zw.gov.mohcc.impilo.paediatrics.age;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgeCalculatorTest {

    @Test
    void babyBornTodayIsZeroDaysOld() {
        LocalDate birth = LocalDate.parse("2026-07-26");
        assertEquals(0, AgeCalculator.ageDays(birth, birth));
        assertEquals(0, AgeCalculator.ageMonths(birth, birth));
        assertEquals(0, AgeCalculator.ageYears(birth, birth));
    }

    @Test
    void ageInMonthsFollowsTheCalendarNotThirtyDayBlocks() {
        // 60 days is not two months: February is short, so the second month completes later.
        LocalDate birth = LocalDate.parse("2026-01-01");
        assertEquals(1, AgeCalculator.ageMonths(birth, LocalDate.parse("2026-02-01")));
        assertEquals(1, AgeCalculator.ageMonths(birth, LocalDate.parse("2026-02-28")));
        assertEquals(2, AgeCalculator.ageMonths(birth, LocalDate.parse("2026-03-01")));
        assertEquals(59, AgeCalculator.ageDays(birth, LocalDate.parse("2026-03-01")));
    }

    @Test
    void monthEndBirthdaysCompleteAMonthLateNotEarly() {
        // Born 31 January. A minimum-age gate must not open on 28 February.
        LocalDate birth = LocalDate.parse("2026-01-31");
        assertEquals(0, AgeCalculator.ageMonths(birth, LocalDate.parse("2026-02-28")));
        assertEquals(1, AgeCalculator.ageMonths(birth, LocalDate.parse("2026-03-01")));
    }

    @Test
    void leapDayBirthdayIsHandledWithoutRollover() {
        LocalDate birth = LocalDate.parse("2024-02-29");
        assertEquals(0, AgeCalculator.ageYears(birth, LocalDate.parse("2025-02-28")));
        assertEquals(1, AgeCalculator.ageYears(birth, LocalDate.parse("2025-03-01")));
        assertEquals(365, AgeCalculator.ageDays(birth, LocalDate.parse("2025-02-28")));
    }

    @Test
    void weeksAreCompletedWeeksOfLife() {
        LocalDate birth = LocalDate.parse("2026-01-01");
        assertEquals(0, AgeCalculator.ageWeeks(birth, LocalDate.parse("2026-01-07")));
        assertEquals(1, AgeCalculator.ageWeeks(birth, LocalDate.parse("2026-01-08")));
    }

    @Test
    void unknownOrFutureDatesYieldNoAgeRatherThanZero() {
        LocalDate birth = LocalDate.parse("2026-01-01");
        assertNull(AgeCalculator.ageDays(null, LocalDate.parse("2026-01-02")));
        assertNull(AgeCalculator.ageDays(birth, (LocalDate) null));
        assertNull(AgeCalculator.ageDays(birth, LocalDate.parse("2025-12-31")));
        assertNull(AgeCalculator.ageMonths(birth, LocalDate.parse("2025-12-31")));
        assertNull(AgeCalculator.snapshot(birth, LocalDate.parse("2025-12-31")));
    }

    @Test
    void acceptsAnOffsetDateTimeReference() {
        LocalDate birth = LocalDate.parse("2026-01-01");
        assertEquals(10, AgeCalculator.ageDays(birth, OffsetDateTime.parse("2026-01-11T06:30:00Z")));
    }

    @Test
    void snapshotCarriesEveryUnitCliniciansUse() {
        AgeCalculator.AgeSnapshot snapshot =
                AgeCalculator.snapshot(LocalDate.parse("2024-07-26"), LocalDate.parse("2026-07-26"));
        assertEquals(730, snapshot.days());
        assertEquals(104, snapshot.weeks());
        assertEquals(24, snapshot.months());
        assertEquals(2, snapshot.years());
    }
}
