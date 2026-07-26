package zw.gov.mohcc.impilo.paediatrics.vitals;

import java.util.List;

/**
 * Age-banded alert thresholds for vital signs.
 *
 * <p>Applying adult thresholds to a child fails in both directions at once. A tachycardia
 * alert at 120 bpm fires on nearly every well infant, and an alert stream that is usually
 * wrong stops being read. A bradycardia alert at 50 bpm never fires for a neonate whose
 * heart rate has fallen to 80 — which is a peri-arrest finding. One misses nothing and
 * warns constantly; the other stays quiet through an emergency.</p>
 *
 * <p>These are ALERT thresholds — the point at which a finding is worth interrupting a
 * clinician — and are deliberately separate from scoring tables, which award points on a
 * graded scale. Both are age-banded, but they answer different questions.</p>
 *
 * <p><strong>Engineering seed.</strong> The bands follow conventional paediatric vital-sign
 * ranges but require MoHCC and paediatric specialist ratification before they drive
 * interruptive alerts in production.</p>
 */
public final class VitalAlertThresholds {

    public static final String CONTENT_VERSION = "engineering-seed-1.0.0";

    /** From this age the adult thresholds apply. */
    public static final int ADULT_FROM_AGE_YEARS = 13;

    // Bands are contiguous and cover every age from birth; the last one is open-ended so no
    // patient can fall between bands and silently lose their thresholds.
    private static final List<Band> BANDS = List.of(
            //         maxAgeDays,  tachy, brady, hypotensive systolic, hypertensive systolic
            new Band("NEONATE_0_28D",       28,   180,   100,  50,  110),
            new Band("INFANT_1_11M",       364,   180,    90,  60,  115),
            new Band("YOUNG_CHILD_1_4Y",  1824,   160,    80,  70,  120),
            new Band("SCHOOL_AGE_5_11Y",  4379,   140,    60,  80,  130),
            new Band("ADOLESCENT_12Y",   Integer.MAX_VALUE, 120, 50, 90, 180));

    private VitalAlertThresholds() {
    }

    /** True once the adult thresholds are the right ones to apply. */
    public static boolean adultThresholdsApply(Double ageYears, Integer ageDays) {
        if (ageDays != null) {
            return ageDays >= ADULT_FROM_AGE_YEARS * 365;
        }
        return ageYears != null && ageYears >= ADULT_FROM_AGE_YEARS;
    }

    /**
     * The band for a patient's age, or null when age is unknown. A null result means the
     * caller must not guess: scoring a child against adult thresholds is precisely the
     * failure these bands exist to prevent.
     */
    public static Band bandFor(Double ageYears, Integer ageDays) {
        Integer days = ageDays;
        if (days == null && ageYears != null) {
            days = (int) Math.round(ageYears * 365);
        }
        if (days == null || days < 0) {
            return null;
        }
        for (Band band : BANDS) {
            if (days <= band.maxAgeDays()) {
                return band;
            }
        }
        return BANDS.get(BANDS.size() - 1);
    }

    public static List<Band> bands() {
        return BANDS;
    }

    /**
     * @param tachycardiaAbove       heart rate above this is tachycardic for the band
     * @param bradycardiaBelow       heart rate below this is bradycardic for the band
     * @param hypotensiveSystolicBelow systolic pressure below this indicates shock for the band
     * @param hypertensiveSystolicAtOrAbove systolic pressure at or above this is a crisis for the band
     */
    public record Band(
            String id,
            int maxAgeDays,
            int tachycardiaAbove,
            int bradycardiaBelow,
            int hypotensiveSystolicBelow,
            int hypertensiveSystolicAtOrAbove) {
    }
}
