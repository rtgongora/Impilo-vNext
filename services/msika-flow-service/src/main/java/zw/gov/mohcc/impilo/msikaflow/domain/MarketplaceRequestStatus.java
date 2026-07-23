package zw.gov.mohcc.impilo.msikaflow.domain;

/**
 * Marketplace request (RFO) lifecycle per Vol II §9.3 (OF-B4).
 */
public enum MarketplaceRequestStatus {
    /** Publication decision was not marketplace — terminal placeholder. */
    NOT_REQUIRED,
    /** Request assembled; allow-list validated; not yet published. */
    DRAFT,
    /** Published per publication mode; eligible-vendor set non-empty. */
    PUBLISHED,
    /** Invitations delivered; offer-collection window counting down. */
    COLLECTING_OFFERS,
    /** At least one active offer; selection window armed. */
    OFFERS_AVAILABLE,
    /** Patient opened comparison / commitment in flight. */
    SELECTION_PENDING,
    /** Commitment sequence completed (§8.9) — terminal per round. */
    COMMITTED,
    /** Window closed with zero offers — ops exception, re-publishable. */
    FAILED_NO_OFFERS,
    /** Cancelled by requester; live offers released. Terminal. */
    CANCELLED,
    /** Selection window lapsed unactioned. Terminal (re-run = new round). */
    EXPIRED
}
