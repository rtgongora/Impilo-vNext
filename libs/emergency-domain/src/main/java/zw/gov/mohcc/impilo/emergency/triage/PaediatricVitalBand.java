package zw.gov.mohcc.impilo.emergency.triage;

/**
 * The paediatric chart's age-banded high-risk vital-sign thresholds.
 *
 * <p>Transcribed from the vendored paediatric chart. The adult chart has one band for everyone; the
 * paediatric chart has three, and using the wrong one is silently wrong rather than obviously wrong
 * — a heart rate of 150 is high-risk in a 10-year-old and unremarkable in an infant.
 *
 * <p>Note the bands are printed as "&lt; 1 year / 1-4 years / 5-12 years". The chart itself is for
 * age &lt; 12, so the third band's upper edge is the chart boundary rather than an age.
 */
public enum PaediatricVitalBand {

    /** Under 1 year. RR high 50 / low 25; HR high 180 / low 90. */
    UNDER_1_YEAR("<1 year", 0, 364, 50, 25, 180, 90),

    /** 1 to 4 years. RR high 40 / low 20; HR high 160 / low 80. */
    ONE_TO_FOUR("1-4 years", 365, 1824, 40, 20, 160, 80),

    /** 5 to under 12 years. RR high 30 / low 10; HR high 140 / low 70. */
    FIVE_TO_TWELVE("5-12 years", 1825, 12 * 365 - 1, 30, 10, 140, 70);

    private final String label;
    private final int minDays;
    private final int maxDays;
    private final int rrHigh;
    private final int rrLow;
    private final int hrHigh;
    private final int hrLow;

    PaediatricVitalBand(String label, int minDays, int maxDays,
                        int rrHigh, int rrLow, int hrHigh, int hrLow) {
        this.label = label;
        this.minDays = minDays;
        this.maxDays = maxDays;
        this.rrHigh = rrHigh;
        this.rrLow = rrLow;
        this.hrHigh = hrHigh;
        this.hrLow = hrLow;
    }

    /**
     * The band covering this age. Bands are contiguous and exhaustive across the paediatric chart's
     * range, so there is no hole to fall through — the paediatric PEWS band-hole defect is the
     * reason that property is asserted in a test rather than assumed from reading.
     */
    public static PaediatricVitalBand forAgeDays(int ageDays) {
        for (PaediatricVitalBand b : values()) {
            if (ageDays >= b.minDays && ageDays <= b.maxDays) return b;
        }
        throw new IllegalArgumentException(
                "Age " + ageDays + " days is outside the paediatric chart; use the adult chart.");
    }

    public String label() { return label; }
    public int minDays() { return minDays; }
    public int maxDays() { return maxDays; }
    public int rrHigh() { return rrHigh; }
    public int rrLow() { return rrLow; }
    public int hrHigh() { return hrHigh; }
    public int hrLow() { return hrLow; }
}
