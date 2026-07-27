package zw.gov.mohcc.impilo.paediatrics.growth;

import java.util.Locale;
import java.util.Set;

/**
 * The growth references this engine can score against.
 *
 * <p>Which standard applies to a child is decided by the engine from gestational age and
 * postmenstrual age, never by the caller. "Score this preterm baby against the term chart"
 * is not a request the system should be able to express, because the answer would look
 * like a severe-malnutrition finding on a baby who is growing exactly as expected.</p>
 *
 * <p>Each standard carries the identifier stamped onto every score it produces, the axis it
 * is indexed on, and the attribution its licence requires. The attribution travels with the
 * data through the API to the screen, so a surface cannot render a chart without it.</p>
 */
public enum GrowthStandard {

    /**
     * WHO Child Growth Standards 2006, birth to five years, indexed by age in days —
     * corrected age where the child was born preterm.
     */
    WHO_2006_CHILD_GROWTH_STANDARDS(
            "WHO_2006_CHILD_GROWTH_STANDARDS",
            "WHO Child Growth Standards (2006)",
            "growth/who_under5_lms.json",
            Axis.AGE_DAYS,
            Set.of(GrowthIndicator.WEIGHT_FOR_AGE,
                    GrowthIndicator.LENGTH_HEIGHT_FOR_AGE,
                    GrowthIndicator.BODY_MASS_INDEX_FOR_AGE,
                    GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE),
            null,
            "WHO Multicentre Growth Reference Study Group. WHO Child Growth Standards."
                    + " Geneva: World Health Organization, 2006.",
            null),

    /**
     * Fenton 2013 preterm growth chart, indexed by completed postmenstrual weeks.
     *
     * <p>Scores are read at whole completed weeks, which is how the published tables are
     * expressed and how the authors' own calculator reads them. The engine does not
     * interpolate between the published weekly points: an interpolated value is a number
     * the authors did not publish, and the licence this data carries does not permit
     * deriving new values from it.</p>
     */
    FENTON_2013_PRETERM_GROWTH(
            "FENTON_2013",
            "Fenton 2013 Preterm Growth Chart",
            "growth/fenton_2013_preterm_lms.json",
            Axis.POSTMENSTRUAL_WEEKS,
            Set.of(GrowthIndicator.WEIGHT_FOR_AGE,
                    GrowthIndicator.LENGTH_HEIGHT_FOR_AGE,
                    GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE),
            "Fenton 2013 Preterm Growth Chart",
            "Fenton TR, Kim JH. A systematic review and meta-analysis to revise the Fenton"
                    + " growth chart for preterm infants. BMC Pediatr. 2013;13:59.",
            "CC BY-NC-ND 4.0");

    /** What the reference tables are indexed on. */
    public enum Axis {
        /** Age in days since birth (corrected age for preterm children). */
        AGE_DAYS,
        /** Completed weeks since the first day of the mother's last menstrual period. */
        POSTMENSTRUAL_WEEKS
    }

    private final String standardId;
    private final String label;
    private final String resourcePath;
    private final Axis axis;
    private final Set<GrowthIndicator> indicators;
    private final String requiredChartLabel;
    private final String citation;
    private final String licence;

    GrowthStandard(String standardId,
                   String label,
                   String resourcePath,
                   Axis axis,
                   Set<GrowthIndicator> indicators,
                   String requiredChartLabel,
                   String citation,
                   String licence) {
        this.standardId = standardId;
        this.label = label;
        this.resourcePath = resourcePath;
        this.axis = axis;
        this.indicators = indicators;
        this.requiredChartLabel = requiredChartLabel;
        this.citation = citation;
        this.licence = licence;
    }

    /** The identifier stamped onto every score produced against this standard. */
    public String standardId() {
        return standardId;
    }

    /** Human-readable name for a clinician-facing surface. */
    public String label() {
        return label;
    }

    public String resourcePath() {
        return resourcePath;
    }

    public Axis axis() {
        return axis;
    }

    /** Indicators this standard publishes tables for. */
    public Set<GrowthIndicator> indicators() {
        return indicators;
    }

    public boolean covers(GrowthIndicator indicator) {
        return indicators.contains(indicator);
    }

    /**
     * The label the data licence requires to be shown conspicuously on any chart drawn from
     * this standard, or null where the licence imposes no such condition.
     */
    public String requiredChartLabel() {
        return requiredChartLabel;
    }

    public String citation() {
        return citation;
    }

    /** Licence identifier where the data carries one, otherwise null. */
    public String licence() {
        return licence;
    }

    /** Resolve a stamped standard identifier back to its standard, or null if unknown. */
    public static GrowthStandard fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalised = id.trim().toUpperCase(Locale.ROOT);
        for (GrowthStandard standard : values()) {
            if (standard.standardId.equals(normalised) || standard.name().equals(normalised)) {
                return standard;
            }
        }
        return null;
    }
}
