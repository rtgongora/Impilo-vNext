package zw.gov.mohcc.impilo.offlinesync.domain;

/**
 * Enumeration of possible sync pack statuses.
 */
public enum SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    CONFLICT,
    FAILED
}
