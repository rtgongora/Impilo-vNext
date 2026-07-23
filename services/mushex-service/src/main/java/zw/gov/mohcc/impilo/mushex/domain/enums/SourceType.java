package zw.gov.mohcc.impilo.mushex.domain.enums;

public enum SourceType {
    COSTA_BILL,
    MSIKA_ORDER,
    /** OF-B10 — marketplace selection shortfall (msika-flow §8.9 step 8; one intent per obligation). */
    MSIKA_SELECTION,
    ADHOC,
    /** Council-regulated provider fees (applications, renewals, penalties, etc.). */
    PROVIDER_COUNCIL_FEE,
    /** HPA facility registration/renewal fees (SI 78 of 2017), raised by TUSO. */
    HPA_FACILITY_FEE
}
