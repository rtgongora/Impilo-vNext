package zw.gov.mohcc.impilo.rito.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.rito.core.ReputationAggregationService;
import zw.gov.mohcc.impilo.rito.persistence.repository.ProviderRatingRepository;

import java.util.UUID;

/**
 * Periodically recomputes provider reputation summaries from PUBLISHED ratings (RW4).
 * Disabled under the {@code test} profile (no scheduler needed for unit tests); the
 * on-demand path {@link ReputationAggregationService#recomputeForProvider} is used there.
 */
@Component
@Profile("!test")
public class RitoReputationAggregator {

    private static final Logger log = LoggerFactory.getLogger(RitoReputationAggregator.class);

    private final ProviderRatingRepository ratingRepository;
    private final ReputationAggregationService aggregationService;

    public RitoReputationAggregator(ProviderRatingRepository ratingRepository,
                                    ReputationAggregationService aggregationService) {
        this.ratingRepository = ratingRepository;
        this.aggregationService = aggregationService;
    }

    @Scheduled(fixedDelayString = "${rito.reputation.recompute-interval-ms:60000}")
    public void recompute() {
        int providers = 0;
        for (Object[] row : ratingRepository.findDistinctPublishedProviderPeriods()) {
            try {
                aggregationService.recomputeForProvider((UUID) row[0], (String) row[1], (String) row[2]);
                providers++;
            } catch (RuntimeException e) {
                log.warn("Provider reputation recompute failed for row: {}", e.getMessage());
            }
        }
        int facilities = 0;
        for (Object[] row : ratingRepository.findDistinctPublishedFacilityPeriods()) {
            try {
                aggregationService.recomputeForFacility((UUID) row[0], (UUID) row[1], (String) row[2]);
                facilities++;
            } catch (RuntimeException e) {
                log.warn("Facility reputation recompute failed for row: {}", e.getMessage());
            }
        }
        if (providers > 0 || facilities > 0) {
            log.debug("Reputation aggregator recomputed {} provider + {} facility rollups", providers, facilities);
        }
    }
}
