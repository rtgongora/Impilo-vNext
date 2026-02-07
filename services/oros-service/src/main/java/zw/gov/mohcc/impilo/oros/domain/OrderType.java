package zw.gov.mohcc.impilo.oros.domain;

/**
 * Classification of clinical orders by fulfillment domain.
 *
 * <p>Determines the routing strategy, workstep template, and SLA profile
 * applied to each order. A facility's {@code oros_capabilities} configuration
 * specifies which order types are supported and how they are fulfilled
 * (internally or via an external adapter).</p>
 */
public enum OrderType {
    /** Laboratory investigations (blood, urine, CSF, etc.). */
    LAB,
    /** Diagnostic imaging (X-ray, ultrasound, CT, MRI). */
    IMAGING,
    /** Medication dispensing (inpatient or outpatient pharmacy). */
    PHARMACY,
    /** Clinical procedures (biopsies, minor surgeries, etc.). */
    PROCEDURE,
    /** Catch-all for order types not covered by specific categories. */
    OTHER
}
