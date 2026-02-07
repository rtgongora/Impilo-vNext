package zw.gov.mohcc.impilo.oros.domain;

/**
 * Classification of acknowledgement events for orders.
 *
 * <ul>
 *   <li>{@code DEPARTMENT} — fulfilling department has acknowledged receipt
 *       of the order (e.g. lab, radiology, pharmacy)</li>
 *   <li>{@code CLINICIAN} — ordering clinician has reviewed the result</li>
 *   <li>{@code CRITICAL} — critical/panic value has been acknowledged by
 *       the responsible clinician (regulatory requirement)</li>
 * </ul>
 */
public enum AckType {
    DEPARTMENT,
    CLINICIAN,
    CRITICAL
}
