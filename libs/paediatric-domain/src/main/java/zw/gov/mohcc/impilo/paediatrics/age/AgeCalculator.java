package zw.gov.mohcc.impilo.paediatrics.age;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/**
 * Chronological age arithmetic for the paediatric pathway.
 *
 * <p>Paediatric routing, dosing and growth interpretation all change behaviour within
 * days of birth, so age is computed to the day rather than to the year. Every accessor
 * returns {@code null} rather than a default when the inputs cannot support an answer:
 * an unknown age must never be silently treated as an adult age.</p>
 */
public final class AgeCalculator {

    private AgeCalculator() {
    }

    /** Whole days lived at {@code reference}; null when either date is missing or the reference precedes birth. */
    public static Integer ageDays(LocalDate dateOfBirth, LocalDate reference) {
        if (dateOfBirth == null || reference == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(dateOfBirth, reference);
        return days < 0 ? null : (int) days;
    }

    public static Integer ageDays(LocalDate dateOfBirth, OffsetDateTime reference) {
        return reference == null ? null : ageDays(dateOfBirth, reference.toLocalDate());
    }

    /** Completed weeks of life. */
    public static Integer ageWeeks(LocalDate dateOfBirth, LocalDate reference) {
        Integer days = ageDays(dateOfBirth, reference);
        return days == null ? null : days / 7;
    }

    /**
     * Completed calendar months, not days/30, because immunisation minimum ages and dosing
     * age bands are written in calendar months.
     *
     * <p>Where the birth day-of-month does not exist in the target month, a month is
     * counted as complete only once the day-of-month is reached or passed: a child born on
     * 31 January completes one month on 1 March, not 28 February. That is the later of the
     * two defensible readings, and the safe one — a minimum-age gate must never open early.</p>
     */
    public static Integer ageMonths(LocalDate dateOfBirth, LocalDate reference) {
        if (dateOfBirth == null || reference == null || reference.isBefore(dateOfBirth)) {
            return null;
        }
        return (int) ChronoUnit.MONTHS.between(dateOfBirth, reference);
    }

    /** Completed calendar years. */
    public static Integer ageYears(LocalDate dateOfBirth, LocalDate reference) {
        if (dateOfBirth == null || reference == null || reference.isBefore(dateOfBirth)) {
            return null;
        }
        return Period.between(dateOfBirth, reference).getYears();
    }

    /** Full breakdown, or null when age cannot be established. */
    public static AgeSnapshot snapshot(LocalDate dateOfBirth, LocalDate reference) {
        Integer days = ageDays(dateOfBirth, reference);
        if (days == null) {
            return null;
        }
        return new AgeSnapshot(days, days / 7, ageMonths(dateOfBirth, reference), ageYears(dateOfBirth, reference));
    }

    public record AgeSnapshot(int days, int weeks, int months, int years) {
    }
}
