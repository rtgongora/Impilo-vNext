package zw.gov.mohcc.impilo.pharmacy.domain;

/**
 * eLMIS integration mode for the pharmacy service.
 *
 * <p>Configurable per deployment via {@code pharmacy.elmis.mode} in
 * application.yml. Determines how stock data flows between the
 * pharmacy service and the national eLMIS.</p>
 */
public enum ElmisMode {

    /** No external eLMIS; pharmacy service manages stock autonomously. */
    INTERNAL,

    /** All stock operations route through the pharmacy-elmis-adapter. */
    ADAPTER,

    /**
     * Dual-write mode: stock managed internally with shadow writes to
     * eLMIS via the adapter, plus periodic reconciliation.
     */
    HYBRID
}
