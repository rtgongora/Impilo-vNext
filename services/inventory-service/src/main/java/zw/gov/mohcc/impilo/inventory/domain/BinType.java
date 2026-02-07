package zw.gov.mohcc.impilo.inventory.domain;

/**
 * Classification of storage bins within a store ({@code inv_bins.bin_type}).
 *
 * <p>Bins subdivide a store into logical or physical storage locations.
 * The bin type drives storage rules (e.g. temperature monitoring for
 * cold-chain, access control for controlled substances).</p>
 */
public enum BinType {

    /** Standard shelf storage at ambient temperature. */
    SHELF,

    /** Temperature-controlled storage (typically 2-8 degrees Celsius) for vaccines and biologicals. */
    COLD_CHAIN,

    /** Restricted-access storage for scheduled/controlled substances. */
    CONTROLLED,

    /** Hazardous materials storage requiring special handling. */
    HAZARDOUS,

    /** General purpose bin (default). */
    GENERAL,

    /** Quarantine area for damaged, suspect, or recalled stock pending disposition. */
    QUARANTINE
}
