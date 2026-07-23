package zw.gov.mohcc.impilo.pharmacy.domain;

/**
 * OF-B15 — lifecycle of a prescriber clarification request
 * ({@code rx_clarification_requests.status}).
 *
 * <p>The dispense episode holds (cannot complete) while any request is
 * PENDING; DECLINED is fail-closed — the proposed substitution is never
 * applied.</p>
 */
public enum ClarificationStatus {

    /** Awaiting the prescriber's decision; the episode holds. */
    PENDING,

    /** Prescriber approved — the substitution may be applied exactly once. */
    APPROVED,

    /** Prescriber declined — fail-closed, substitution never applied. Terminal. */
    DECLINED,

    /** Withdrawn by the pharmacy (e.g. episode cancelled). Terminal. */
    CANCELLED
}
