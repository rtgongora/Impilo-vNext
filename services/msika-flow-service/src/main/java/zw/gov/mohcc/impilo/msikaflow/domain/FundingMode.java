package zw.gov.mohcc.impilo.msikaflow.domain;

/**
 * OF-B8/OF-B9 — the request's funding-source election (§10.3/§10.4).
 *
 * <p>{@code SELF_PAY} is the explicit out-of-pocket election: no coverage
 * engines are consulted and the full offer price is the patient's due-now
 * shortfall. {@code PAYER_COVERED} routes every offer through the
 * COSTA-charge → Ruvimbo-liability ladder and FAILS CLOSED at commitment
 * step 7 when the engines are unreachable (§10.4/§10.5) — an unverifiable
 * payer promise is never silently converted into a patient debt.</p>
 */
public enum FundingMode {
    SELF_PAY,
    PAYER_COVERED
}
