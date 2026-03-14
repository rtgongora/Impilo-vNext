package zw.gov.mohcc.impilo.inventory.elmis.domain;

/**
 * Status of a sync operation between the inventory service and eLMIS.
 */
public enum SyncStatus {

    /** No sync in progress. */
    IDLE,

    /** Sync is currently running. */
    IN_PROGRESS,

    /** Sync completed successfully. */
    COMPLETED,

    /** Sync failed with an error. */
    FAILED
}
