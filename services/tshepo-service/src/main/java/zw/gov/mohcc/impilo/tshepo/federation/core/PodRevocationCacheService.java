package zw.gov.mohcc.impilo.tshepo.federation.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process revocation cache for federation pods.
 *
 * Provides O(1) lookup to check if a pod has been revoked, enabling
 * real-time enforcement on every federated request without a DB round-trip.
 *
 * In production, this is backed by Redis for cross-instance consistency.
 * The in-process set acts as L1 cache and is populated at startup from
 * the database, then kept in sync via Kafka control channel events.
 *
 * Revocation entries never expire — pods must be explicitly reinstated.
 */
@Service
public class PodRevocationCacheService {

    private static final Logger log = LoggerFactory.getLogger(PodRevocationCacheService.class);

    private final Set<String> revokedPods = ConcurrentHashMap.newKeySet();

    /**
     * Check if a pod is currently revoked.
     *
     * @param podId the pod identifier to check
     * @return true if the pod is in the revocation set
     */
    public boolean isRevoked(String podId) {
        return revokedPods.contains(podId);
    }

    /**
     * Mark a pod as revoked. Called on:
     * 1. Admin revocation action (immediate, same instance)
     * 2. Kafka control channel POD_REVOKED event (cross-instance propagation)
     * 3. Startup hydration from database
     */
    public void markRevoked(String podId) {
        revokedPods.add(podId);
        log.info("Pod added to revocation cache: {}", podId);
    }

    /**
     * Remove a pod from the revocation set (reinstatement).
     */
    public void clearRevocation(String podId) {
        revokedPods.remove(podId);
        log.info("Pod removed from revocation cache: {}", podId);
    }

    /**
     * Bulk-load revoked pods (used at startup to hydrate from DB).
     */
    public void loadAll(Iterable<String> podIds) {
        for (String id : podIds) {
            revokedPods.add(id);
        }
        log.info("Revocation cache hydrated with {} entries", revokedPods.size());
    }

    /**
     * Get count of revoked pods (for metrics).
     */
    public int revokedCount() {
        return revokedPods.size();
    }
}
