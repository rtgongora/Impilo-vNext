package zw.gov.mohcc.impilo.zibo.domain;

/**
 * Severity levels for terminology validation outcomes.
 *
 * <p>Aligns with the FHIR OperationOutcome issue severity codes:</p>
 * <ul>
 *   <li>{@link #ERROR} -- the code or resource is invalid and must be corrected</li>
 *   <li>{@link #WARNING} -- the code is questionable but processing can continue</li>
 *   <li>{@link #INFORMATION} -- informational note that does not affect validity</li>
 * </ul>
 */
public enum ValidationSeverity {
    ERROR,
    WARNING,
    INFORMATION
}
