package zw.gov.mohcc.impilo.federation.identity;

/**
 * Checks whether a pod has been revoked.
 *
 * Services implement this interface to plug into their revocation
 * infrastructure (Redis, in-memory cache, etc.). The federation
 * filter calls this before allowing a request through.
 */
public interface PodRevocationChecker {

    /**
     * Check if a pod is currently revoked.
     *
     * @param podId the pod identifier to check
     * @return true if the pod is revoked and should be blocked
     */
    boolean isRevoked(String podId);
}
