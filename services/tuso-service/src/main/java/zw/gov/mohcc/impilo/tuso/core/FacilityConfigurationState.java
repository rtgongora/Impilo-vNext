package zw.gov.mohcc.impilo.tuso.core;

/**
 * Computed lifecycle state of a facility's operational configuration (service points, workspaces,
 * derived queue definitions). This is a <b>derived</b> read-model value — it is not persisted; it is
 * recomputed from live TUSO entities each time the configuration console reads it.
 *
 * <p>Imported facilities (regulatory status {@code IMPORTED_PENDING_CONFIGURATION}) remain reported as
 * {@link #IMPORTED_PENDING_CONFIGURATION} here until the required configuration sections (facility code,
 * at least one active service point, at least one active workspace) are complete. Nothing is faked: the
 * state only advances when the underlying entities actually exist.</p>
 */
public enum FacilityConfigurationState {
    /** Absorbed from a master list; required operational configuration not yet done. */
    IMPORTED_PENDING_CONFIGURATION,
    /** No departments, service points or workspaces configured yet. */
    NOT_STARTED,
    /** Some configuration exists but required sections are incomplete. */
    IN_PROGRESS,
    /** Configuration exists and is awaiting an administrator review before publish. */
    READY_FOR_REVIEW,
    /** All required sections complete. */
    CONFIGURED,
    /** Some but not all required sections complete. */
    PARTIALLY_CONFIGURED,
    /** Configuration present but flagged for attention (e.g. inconsistent state). */
    NEEDS_ATTENTION,
    /**
     * TUSO configuration changed after downstream (PCT) last materialised — a reconcile is due.
     * Only asserted when a live downstream signal is available.
     */
    STALE_DOWNSTREAM,
    /** Downstream (PCT) has materialised the derived queue definitions. Live-signal gated. */
    MATERIALISED,
    /** Downstream materialisation failed. Live-signal gated. */
    FAILED_MATERIALISATION
}
