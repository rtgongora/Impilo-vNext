package zw.gov.mohcc.impilo.inventory.domain;

/** State of an external sync record. */
public enum SyncStatus {
    /** Awaiting transmission. */
    PENDING,
    /** Successfully acknowledged by the external system. */
    SYNCED,
    /** Failed and exhausted automatic retries — needs manual replay. */
    FAILED,
    /** Failed but eligible for another automatic attempt. */
    RETRY
}
