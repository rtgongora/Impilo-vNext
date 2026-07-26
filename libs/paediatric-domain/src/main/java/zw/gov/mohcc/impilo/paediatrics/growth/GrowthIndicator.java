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

    WEIGHT_FOR_AGE("weight_for_age"),
    LENGTH_HEIGHT_FOR_AGE("length_height_for_age"),
    BODY_MASS_INDEX_FOR_AGE("body_mass_index_for_age"),
    HEAD_CIRCUMFERENCE_FOR_AGE("head_circumference_for_age");

    private final String datasetKey;

    GrowthIndicator(String datasetKey) {
        this.datasetKey = datasetKey;
    }

    public String datasetKey() {
        return datasetKey;
    }
}
