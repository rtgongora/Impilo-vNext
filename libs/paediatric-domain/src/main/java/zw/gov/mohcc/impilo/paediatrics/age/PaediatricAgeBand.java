package zw.gov.mohcc.impilo.paediatrics.age;

/**
 * Canonical paediatric age bands used to route a child to the correct clinical pathway.
 *
 * <p>The bands are contiguous and exhaustive from birth: every age in days falls in
 * exactly one band. Two clinically important groupings deliberately cut across bands
 * and are therefore modelled as predicates on {@link PaediatricAgeRouter} rather than
 * as bands of their own: the birth episode (a delivery presentation, not an age) and
 * the sick young infant (0–59 days, spanning both neonatal bands and early infancy).</p>
 */
public enum PaediatricAgeBand {

    /** Early newborn, 0–7 days. */
    NEWBORN_EARLY(0, 7),

    /** Late newborn, 8–28 days. */
    NEWBORN_LATE(8, 28),

    /** Young infant beyond the neonatal period, 29 days–11 completed months. */
    INFANT(29, 364),

    /** Young child, 12–59 completed months. */
    YOUNG_CHILD(365, 1824),

    /** School-age child, 5–9 years. */
    SCHOOL_AGE(1825, 3649),

    /** Young adolescent, 10–14 years. */
    ADOLESCENT_YOUNG(3650, 5474),

    /** Older adolescent, 15–17 years. */
    ADOLESCENT_OLDER(5475, 6574),

    /** 18 years and above: paediatric services hand over to adult care. */
    ADULT_TRANSITION(6575, Integer.MAX_VALUE);

    private final int minAgeDaysInclusive;
    private final int maxAgeDaysInclusive;

    PaediatricAgeBand(int minAgeDaysInclusive, int maxAgeDaysInclusive) {
        this.minAgeDaysInclusive = minAgeDaysInclusive;
        this.maxAgeDaysInclusive = maxAgeDaysInclusive;
    }

    public int minAgeDaysInclusive() {
        return minAgeDaysInclusive;
    }

    public int maxAgeDaysInclusive() {
        return maxAgeDaysInclusive;
    }

    public boolean covers(int ageDays) {
        return ageDays >= minAgeDaysInclusive && ageDays <= maxAgeDaysInclusive;
    }

    /** True for bands still served by paediatric services. */
    public boolean paediatric() {
        return this != ADULT_TRANSITION;
    }
}
