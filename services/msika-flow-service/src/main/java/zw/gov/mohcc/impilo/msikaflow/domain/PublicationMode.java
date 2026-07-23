package zw.gov.mohcc.impilo.msikaflow.domain;

/**
 * RFO publication modes per Vol II §11.2.
 *
 * <p>Wave OF-A supports {@link #OPEN} (non-controlled only — §13.4),
 * {@link #INVITED}, {@link #PATIENT_REQUESTED_CHECK} and {@link #GEO_RADIUS}
 * (treated as INVITED with a zone-derived vendor set). The remaining modes are
 * declared for enum completeness but rejected as unsupported until their
 * governing policy work (OD-12 / payer-network encoding) lands.</p>
 */
public enum PublicationMode {
    /** All eligible vendors in scope. NEVER for controlled lines (§13.4). */
    OPEN,
    /** Named / short-listed eligible vendors. Default for controlled. */
    INVITED,
    /** Scheme's contracted network only. Unsupported this wave. */
    PAYER_NETWORK,
    /** Eligible vendors within N zones of the anchor (coarse ndila zones). */
    GEO_RADIUS,
    /** Capability-flag-scoped. Unsupported this wave. */
    SPECIALITY,
    /** Public facilities/pharmacies only. Unsupported this wave. */
    PUBLIC_SECTOR,
    /** Ordering facility's own fulfilment points. Unsupported this wave. */
    FACILITY_OWNED,
    /** No competition; §10.8 regulated bypass only. Unsupported this wave. */
    EMERGENCY_DIRECT,
    /** Preferred vendor cascade. Unsupported this wave. */
    SEQUENTIAL_FALLBACK,
    /** Patient asks a specific vendor "can you fulfil this?" — single invitation. */
    PATIENT_REQUESTED_CHECK,
    /** Read-only browse; converts to INVITED on request. Unsupported this wave. */
    SEARCH_WITHOUT_AUCTION
}
