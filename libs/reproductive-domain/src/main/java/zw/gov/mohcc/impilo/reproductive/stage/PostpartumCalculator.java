package zw.gov.mohcc.impilo.reproductive.stage;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Time since the end of a pregnancy, and the windows that hang off it.
 *
 * <p>The postnatal contact schedule is deliberately NOT here — when a contact is due is a national
 * programme decision and belongs in governed content, like the immunisation schedule. This class
 * owns only the arithmetic: what day it is, which band that falls in, and whether a death or a
 * haemorrhage falls inside a defined window.
 */
public final class PostpartumCalculator {

    /** WHO: a maternal death is one while pregnant or within 42 days of the end of pregnancy. */
    public static final int MATERNAL_DEATH_WINDOW_DAYS = 42;

    /** A late maternal death is between 43 days and one year. */
    public static final int LATE_MATERNAL_DEATH_WINDOW_DAYS = 365;

    /** Primary postpartum haemorrhage: within 24 hours of birth. */
    public static final int PRIMARY_PPH_MAX_HOURS = 24;

    /** Secondary postpartum haemorrhage: from 24 hours to 12 weeks. */
    public static final int SECONDARY_PPH_MAX_DAYS = 84;

    private PostpartumCalculator() {
    }

    /** Completed days since the pregnancy ended; the day of birth is day 0. */
    public static Integer postpartumDay(LocalDate pregnancyEndedOn, LocalDate on) {
        if (pregnancyEndedOn == null || on == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(pregnancyEndedOn, on);
        return days < 0 ? null : (int) days;
    }

    public static Integer postpartumDay(OffsetDateTime pregnancyEndedAt, OffsetDateTime at) {
        if (pregnancyEndedAt == null || at == null) {
            return null;
        }
        long hours = ChronoUnit.HOURS.between(pregnancyEndedAt, at);
        return hours < 0 ? null : (int) (hours / 24);
    }

    public static Integer hoursSinceBirth(OffsetDateTime bornAt, OffsetDateTime at) {
        if (bornAt == null || at == null) {
            return null;
        }
        long hours = ChronoUnit.HOURS.between(bornAt, at);
        return hours < 0 ? null : (int) hours;
    }

    /**
     * Whether a death at this postpartum day falls in the maternal-death window. Null when the day
     * is unknown: a death that cannot be placed in time must be reviewed, not excluded by default.
     */
    public static Boolean withinMaternalDeathWindow(Integer postpartumDay) {
        return postpartumDay == null ? null : postpartumDay <= MATERNAL_DEATH_WINDOW_DAYS;
    }

    public static Boolean withinLateMaternalDeathWindow(Integer postpartumDay) {
        if (postpartumDay == null) {
            return null;
        }
        return postpartumDay > MATERNAL_DEATH_WINDOW_DAYS
                && postpartumDay <= LATE_MATERNAL_DEATH_WINDOW_DAYS;
    }

    /**
     * Primary or secondary postpartum haemorrhage, by timing alone.
     *
     * <p>The distinction drives completely different causes and management — primary is usually
     * uterine atony, trauma or retained tissue, secondary is usually retained products or
     * endometritis — so it must be recorded rather than inferred later from a date subtraction
     * somebody may do differently.
     */
    public static PphTiming classifyHaemorrhage(OffsetDateTime deliveredAt, OffsetDateTime onsetAt) {
        if (deliveredAt == null || onsetAt == null) {
            return null;
        }
        long hours = ChronoUnit.HOURS.between(deliveredAt, onsetAt);
        if (hours < 0) {
            return null;
        }
        if (hours <= PRIMARY_PPH_MAX_HOURS) {
            return PphTiming.PRIMARY;
        }
        return hours <= (long) SECONDARY_PPH_MAX_DAYS * 24
                ? PphTiming.SECONDARY
                : PphTiming.OUTSIDE_WINDOW;
    }

    public enum PphTiming { PRIMARY, SECONDARY, OUTSIDE_WINDOW }

    /** WHO postnatal bands. */
    public enum PostpartumBand {
        IMMEDIATE(0, 0),
        EARLY(1, 6),
        LATE(7, 41),
        EXTENDED(42, 364);

        private final int minDayInclusive;
        private final int maxDayInclusive;

        PostpartumBand(int minDayInclusive, int maxDayInclusive) {
            this.minDayInclusive = minDayInclusive;
            this.maxDayInclusive = maxDayInclusive;
        }

        public boolean covers(int postpartumDay) {
            return postpartumDay >= minDayInclusive && postpartumDay <= maxDayInclusive;
        }

        public static PostpartumBand ofDay(Integer postpartumDay) {
            if (postpartumDay == null || postpartumDay < 0) {
                return null;
            }
            for (PostpartumBand band : values()) {
                if (band.covers(postpartumDay)) {
                    return band;
                }
            }
            return null;
        }
    }
}
