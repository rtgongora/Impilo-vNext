package zw.gov.mohcc.impilo.tshepo.federation.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.federation.persistence.PodRegistrationRepository;
import zw.gov.mohcc.impilo.tshepo.federation.persistence.PodStatus;

import java.util.List;

/**
 * Hydrates the pod revocation cache at startup from the database.
 *
 * This ensures that even after a service restart, revoked pods are
 * immediately blocked without waiting for a Kafka event replay.
 */
@Component
public class PodRevocationCacheHydrator {

    private static final Logger log = LoggerFactory.getLogger(PodRevocationCacheHydrator.class);

    private final PodRegistrationRepository podRepo;
    private final PodRevocationCacheService revocationCache;

    public PodRevocationCacheHydrator(PodRegistrationRepository podRepo,
                                       PodRevocationCacheService revocationCache) {
        this.podRepo = podRepo;
        this.revocationCache = revocationCache;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void hydrateOnStartup() {
        List<String> revokedPodIds = podRepo.findByStatus(PodStatus.REVOKED)
                .stream()
                .map(pod -> pod.getPodId().toString())
                .toList();

        revocationCache.loadAll(revokedPodIds);
        log.info("Hydrated revocation cache with {} revoked pods from database", revokedPodIds.size());
    }
}
