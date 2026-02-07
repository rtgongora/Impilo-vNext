package zw.gov.mohcc.impilo.pct.domain;

/**
 * Status progression of a discharge case through multiple clearance gates.
 *
 * <p>A discharge case moves through clinical, billing, pharmacy, payment,
 * and summary clearance steps before the patient can exit the facility.</p>
 *
 * <ul>
 *   <li>{@link #INITIATED} — discharge case opened</li>
 *   <li>{@link #CLINICAL_CLEARED} — clinical team has approved discharge</li>
 *   <li>{@link #BILLING_PENDING} — awaiting billing finalization</li>
 *   <li>{@link #PHARMACY_PENDING} — awaiting pharmacy clearance (meds dispensed)</li>
 *   <li>{@link #PAYMENT_PENDING} — awaiting payment clearance</li>
 *   <li>{@link #PAYMENT_CLEARED} — payment confirmed or waived</li>
 *   <li>{@link #SUMMARY_PENDING} — awaiting discharge summary generation</li>
 *   <li>{@link #EXIT_CLEARED} — all gates passed, patient may leave</li>
 *   <li>{@link #COMPLETED} — patient has exited the facility</li>
 *   <li>{@link #CANCELLED} — discharge case was cancelled</li>
 * </ul>
 */
public enum DischargeCaseStatus {
    INITIATED,
    CLINICAL_CLEARED,
    BILLING_PENDING,
    PHARMACY_PENDING,
    PAYMENT_PENDING,
    PAYMENT_CLEARED,
    SUMMARY_PENDING,
    EXIT_CLEARED,
    COMPLETED,
    CANCELLED
}
