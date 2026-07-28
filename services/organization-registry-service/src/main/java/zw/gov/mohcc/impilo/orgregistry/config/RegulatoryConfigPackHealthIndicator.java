package zw.gov.mohcc.impilo.orgregistry.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.orgregistry.core.RegulatoryConfigPackSeeder;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ConfigPackEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.ConfigPackRepository;

import java.util.List;
import java.util.Map;

/**
 * Makes a failed configuration-pack load visible without taking the service down.
 *
 * <p>The status is deliberately {@code DEGRADED} rather than {@code DOWN}. Spring maps DOWN to a
 * 503, which a readiness probe reads as "restart me" — so reporting DOWN would turn a configuration
 * typo into exactly the estate-wide login outage this design exists to avoid. org-registry resolves
 * regulatory appointments, so it must stay up and keep serving the activated release, which was
 * approved separately and is what live cases are pinned to.</p>
 *
 * <p>DEGRADED is not silence either: it surfaces on the health endpoint with the pack, the reason
 * and when it was last checked, so an operator can see what needs resolving.</p>
 */
@Component("regulatoryConfigPacks")
public class RegulatoryConfigPackHealthIndicator implements HealthIndicator {

    static final Status DEGRADED = new Status("DEGRADED",
            "A regulatory configuration pack could not be loaded. Service and activated "
                    + "configuration are unaffected; no further configuration is being seeded from "
                    + "the failed pack.");

    private final ConfigPackRepository packRepository;
    private final ObjectProvider<RegulatoryConfigPackSeeder> seeder;

    public RegulatoryConfigPackHealthIndicator(ConfigPackRepository packRepository,
                                               ObjectProvider<RegulatoryConfigPackSeeder> seeder) {
        this.packRepository = packRepository;
        this.seeder = seeder;
    }

    @Override
    public Health health() {
        List<ConfigPackEntity> failed;
        try {
            failed = packRepository.findByLoadState("LOAD_FAILED");
        } catch (Exception ex) {
            // A health check must never be the thing that breaks the service it reports on.
            return Health.unknown().withDetail("error", ex.getClass().getSimpleName()).build();
        }

        Map<String, String> unrecorded = seeder.getIfAvailable() == null
                ? Map.of() : seeder.getIfAvailable().unrecordedFailures();

        if (failed.isEmpty() && unrecorded.isEmpty()) {
            return Health.up().withDetail("failedPacks", 0).build();
        }

        Health.Builder health = Health.status(DEGRADED)
                .withDetail("failedPacks", failed.size() + unrecorded.size());
        failed.forEach(pack -> health.withDetail(pack.getPackKey(), Map.of(
                "reason", String.valueOf(pack.getLoadFailureReason()),
                "checkedAt", String.valueOf(pack.getLoadCheckedAt()),
                "activatedConfigurationAffected", false)));
        unrecorded.forEach((packKey, reason) -> health.withDetail(packKey, Map.of(
                "reason", reason,
                "activatedConfigurationAffected", false)));
        return health.build();
    }
}
