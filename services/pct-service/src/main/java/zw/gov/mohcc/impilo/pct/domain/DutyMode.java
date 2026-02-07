package zw.gov.mohcc.impilo.pct.domain;

/**
 * Mode of duty for a workspace session.
 *
 * <ul>
 *   <li>{@link #CLINICAL} — clinical care delivery (default)</li>
 *   <li>{@link #ADMIN} — administrative duties (scheduling, billing, etc.)</li>
 *   <li>{@link #VIRTUAL} — remote/telehealth session</li>
 * </ul>
 */
public enum DutyMode {
    CLINICAL,
    ADMIN,
    VIRTUAL
}
