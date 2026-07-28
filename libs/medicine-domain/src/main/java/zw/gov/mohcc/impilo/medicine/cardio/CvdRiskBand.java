package zw.gov.mohcc.impilo.medicine.cardio;

/**
 * The five 10-year cardiovascular risk bands the WHO 2019 risk charts are coloured in.
 *
 * <p>Source: WHO CVD Risk Chart Working Group. <em>World Health Organization cardiovascular disease
 * risk charts: revised models to estimate risk in 21 global regions.</em> Lancet Glob Health
 * 2019;7:e1332–45, and the WHO 2020 guideline <em>HEARTS technical package: risk-based CVD
 * management</em>. The charts themselves are <strong>not vendored</strong> under
 * {@code docs/reference}; only this band vocabulary is transcribed, and it is an engineering seed
 * pending MoHCC ratification.</p>
 *
 * <p><strong>The band set changed between chart generations and they are not interchangeable.</strong>
 * The older WHO/ISH 2007 charts used &lt;10%, 10–&lt;20%, 20–&lt;30%, 30–&lt;40% and ≥40%. The 2019
 * revision uses the five below. A record stamped with one vocabulary and read against the other
 * moves patients by two bands, so every result produced by this package carries its content version
 * and the band set is never renumbered in place.</p>
 *
 * <p>These are ordinal bands, not numbers. There is deliberately no {@code percent()} accessor
 * anywhere in this package — see {@link CvdRiskCalculator} for why.</p>
 */
public enum CvdRiskBand {

    /** Below 5% 10-year risk of a fatal or non-fatal cardiovascular event. */
    BELOW_5("<5%", 0),

    /** 5% to under 10%. */
    FIVE_TO_UNDER_10("5% to <10%", 1),

    /** 10% to under 20%. */
    TEN_TO_UNDER_20("10% to <20%", 2),

    /** 20% to under 30%. */
    TWENTY_TO_UNDER_30("20% to <30%", 3),

    /** 30% or above. */
    THIRTY_OR_MORE(">=30%", 4);

    private final String label;
    private final int ordinalRank;

    CvdRiskBand(String label, int ordinalRank) {
        this.label = label;
        this.ordinalRank = ordinalRank;
    }

    /** The band as the chart legend writes it. */
    public String label() {
        return label;
    }

    /** Rank from 0 (lowest band) to 4 (highest), for ordering and comparison only. */
    public int ordinalRank() {
        return ordinalRank;
    }

    /** True when this band is at least as high as the other. */
    public boolean atLeastAsHighAs(CvdRiskBand other) {
        return other != null && this.ordinalRank >= other.ordinalRank;
    }
}
