package zw.gov.mohcc.impilo.tshepo.federation.persistence;

/**
 * Lifecycle status of a registered federation pod.
 */
public enum PodStatus {
    /** Pod is registered and actively communicating. */
    ACTIVE,
    /** Pod registration is pending approval. */
    PENDING,
    /** Pod has been revoked by an administrator. */
    REVOKED,
    /** Pod registration has expired and must be renewed. */
    EXPIRED,
    /** Pod has been suspended (temporary revocation). */
    SUSPENDED
}
