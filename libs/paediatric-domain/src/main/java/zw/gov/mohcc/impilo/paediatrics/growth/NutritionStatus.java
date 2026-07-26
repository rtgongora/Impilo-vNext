package zw.gov.mohcc.impilo.paediatrics.growth;

/**
 * Nutritional status conclusions the anthropometry can support.
 *
 * <p>Severity ordering matters: when several indicators are positive the most severe
 * conclusion is the one that must drive action, and {@link #atLeastAsSevereAs} exists so
 * that comparison is explicit rather than left to enum ordinal accidents.</p>
 */
public enum NutritionStatus {

    /** Assessment could not be completed; never treat as "adequate". */
    NOT_ASSESSED(0),
    ADEQUATE(1),
    AT_RISK(2),
    MODERATE_ACUTE_MALNUTRITION(3),
    SEVERE_ACUTE_MALNUTRITION(4);

    private final int severity;

    NutritionStatus(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    public boolean atLeastAsSevereAs(NutritionStatus other) {
        return other != null && this.severity >= other.severity;
    }

    public NutritionStatus mostSevere(NutritionStatus other) {
        if (other == null) {
            return this;
        }
        return other.severity > this.severity ? other : this;
    }
}
