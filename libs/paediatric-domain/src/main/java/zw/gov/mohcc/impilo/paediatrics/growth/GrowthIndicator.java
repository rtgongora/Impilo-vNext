package zw.gov.mohcc.impilo.paediatrics.growth;

/**
 * WHO growth indicators this engine can score.
 *
 * <p>Weight-for-length/height — the indicator that defines wasting — is deliberately
 * absent: the WHO length/height-based LMS tables are not part of the embedded dataset,
 * which is indexed by age only. Wasting is therefore assessed from MUAC and bilateral
 * pitting oedema until those tables are added, and the engine reports the indicator as
 * unavailable rather than substituting BMI-for-age, which is a different measurement
 * and would misclassify children.</p>
 */
public enum GrowthIndicator {

    WEIGHT_FOR_AGE("weight_for_age", "kg"),
    LENGTH_HEIGHT_FOR_AGE("length_height_for_age", "cm"),
    BODY_MASS_INDEX_FOR_AGE("body_mass_index_for_age", "kg/m2"),
    HEAD_CIRCUMFERENCE_FOR_AGE("head_circumference_for_age", "cm");

    private final String datasetKey;
    private final String canonicalUnit;

    GrowthIndicator(String datasetKey, String canonicalUnit) {
        this.datasetKey = datasetKey;
        this.canonicalUnit = canonicalUnit;
    }

    public String datasetKey() {
        return datasetKey;
    }

    /**
     * The unit a measurement of this indicator arrives in. A reference table may publish its
     * median in a different unit — Fenton publishes weight in grams — in which case the
     * table declares its own unit and the engine converts before scoring.
     */
    public String canonicalUnit() {
        return canonicalUnit;
    }
}
