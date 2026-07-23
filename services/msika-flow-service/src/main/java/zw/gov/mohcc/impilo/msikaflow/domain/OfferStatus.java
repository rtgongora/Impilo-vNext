package zw.gov.mohcc.impilo.msikaflow.domain;

/**
 * Fulfilment offer lifecycle per Vol II §9.4 (OF-B6).
 *
 * <p>TTL semantics (binding): the offer TTL is the vendor's availability
 * promise; from {@link #SELECTED} onward, offer TTL = reservation TTL — the
 * DURA hold and the offer expire together, fail-close.</p>
 */
public enum OfferStatus {
    /** Vendor composing (vendor-side only). */
    DRAFT,
    /** Vendor submitted; content validation pending. */
    SUBMITTED,
    /** Validation passed; visible in comparison; TTL armed. */
    ACTIVE,
    /** Patient selected; commitment window open (RC-1 single-active). */
    SELECTED,
    /** Commitment steps 3–5 running. */
    REVALIDATING,
    /** Commitment written (step 9). Terminal — fulfilment machine takes over. */
    COMMITTED,
    /** Another offer committed. Terminal. */
    NOT_SELECTED,
    /** Vendor withdrew with reason. Terminal. */
    WITHDRAWN,
    /** TTL lapsed. Terminal — vendor may re-offer while request open. */
    EXPIRED,
    /** Commitment steps 3–6 failed (RC-2/3/4/6/7), failure coded. Terminal. */
    FAILED_REVALIDATION
}
