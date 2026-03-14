package zw.gov.mohcc.impilo.offlinesync.domain;

/**
 * Enumeration of conflict resolution strategies.
 */
public enum ConflictResolution {
    LOCAL_WINS,
    REMOTE_WINS,
    MANUAL_MERGE
}
