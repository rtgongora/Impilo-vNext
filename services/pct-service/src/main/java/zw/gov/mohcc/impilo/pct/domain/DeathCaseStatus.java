package zw.gov.mohcc.impilo.pct.domain;

/**
 * Status progression of a death case through post-mortem workflows.
 *
 * <p>A death case begins when death is pronounced and proceeds through
 * mortuary handling, billing closure, UBOMI (civil registration) notification,
 * and death certificate generation.</p>
 *
 * <ul>
 *   <li>{@link #PRONOUNCED} — death officially pronounced by a clinician</li>
 *   <li>{@link #MORTUARY_PENDING} — awaiting mortuary intake</li>
 *   <li>{@link #BILLING_CLOSED} — all billing for the deceased has been closed</li>
 *   <li>{@link #UBOMI_NOTIFIED} — civil registration (UBOMI) has been notified</li>
 *   <li>{@link #UBOMI_CONFIRMED} — UBOMI has confirmed death registration</li>
 *   <li>{@link #CERT_GENERATED} — death certificate has been generated</li>
 *   <li>{@link #COMPLETED} — all death-case workflows are complete</li>
 * </ul>
 */
public enum DeathCaseStatus {
    PRONOUNCED,
    MORTUARY_PENDING,
    BILLING_CLOSED,
    UBOMI_NOTIFIED,
    UBOMI_CONFIRMED,
    CERT_GENERATED,
    COMPLETED
}
