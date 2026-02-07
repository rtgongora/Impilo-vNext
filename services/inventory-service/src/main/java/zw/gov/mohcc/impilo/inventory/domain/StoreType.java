package zw.gov.mohcc.impilo.inventory.domain;

/**
 * Classification of inventory stores within a facility ({@code inv_stores.store_type}).
 *
 * <p>Each facility may have multiple stores of different types, forming
 * a hierarchy (e.g. a MAIN store supplying subordinate PHARMACY, WARD,
 * and LABORATORY stores).</p>
 */
public enum StoreType {

    /** Central or main store; typically the primary receiving point for the facility. */
    MAIN,

    /** Pharmacy dispensary store for medication and pharmaceutical supplies. */
    PHARMACY,

    /** Laboratory consumables and reagent store. */
    LABORATORY,

    /** Imaging and radiology supplies store. */
    IMAGING,

    /** Ward-level supply store for inpatient units. */
    WARD,

    /** Outpatient department store. */
    OPD,

    /** Operating theatre store for surgical supplies. */
    THEATRE,

    /** General purpose store (default). */
    GENERAL
}
