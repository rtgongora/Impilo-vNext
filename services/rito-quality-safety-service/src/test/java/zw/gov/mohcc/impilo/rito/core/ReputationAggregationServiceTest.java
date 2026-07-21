package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.DomainScoreInput;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderRatingEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderReputationSummaryEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.ProviderReputationSummaryRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReputationAggregationServiceTest {

    @Autowired private RatingService ratingService;
    @Autowired private RatingModerationService moderationService;
    @Autowired private VerifiedInteractionService verifiedInteractionService;
    @Autowired private ReputationAggregationService aggregationService;
    @Autowired private ProviderReputationSummaryRepository summaryRepository;

    private ProviderRatingEntity submitPublished(UUID tenant, String provider, String encounterRef, BigDecimal comm) {
        var req = new zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.SubmitRatingRequest(
                provider, null, null, encounterRef, "ATTENDING", null, null, "CLIENT", true, null, "2026-07",
                comm, null, List.of(new DomainScoreInput("CLIENT_EXPERIENCE", "communication", comm, "1-5")));
        ProviderRatingEntity r = ratingService.submitRating(tenant, req);
        moderationService.moderate(tenant, "reviewer", r.getId(), "PUBLISH", "ok", null);
        return r;
    }

    @Test
    void recomputeProducesVerifiedAndOverallMeans() {
        UUID tenant = UUID.randomUUID();
        String provider = "PROVAGG1234567890123456789";
        String encounterRef = UUID.randomUUID().toString();

        Map<String, Object> pct = new HashMap<>();
        pct.put("encounterRef", encounterRef);
        pct.put("attendingProviderId", provider);
        verifiedInteractionService.recordFromPct(tenant, pct);

        submitPublished(tenant, provider, encounterRef, new BigDecimal("5.0")); // verified
        submitPublished(tenant, provider, null, new BigDecimal("3.0"));         // unverified

        aggregationService.recomputeForProvider(tenant, provider, "2026-07");

        // "All facilities" rollup for CLIENT_EXPERIENCE.
        List<ProviderReputationSummaryEntity> rollups = summaryRepository
                .findByTenantIdAndProviderPublicIdAndFacilityIdIsNullAndServicePointIdIsNullAndReportingPeriod(
                        tenant, provider, "2026-07");
        ProviderReputationSummaryEntity clientExp = rollups.stream()
                .filter(s -> "CLIENT_EXPERIENCE".equals(s.getDomain())).findFirst().orElseThrow();

        assertThat(clientExp.getRatingCount()).isEqualTo(2);
        assertThat(clientExp.getVerifiedCount()).isEqualTo(1);
        assertThat(clientExp.getMeanScore()).isEqualByComparingTo("4.00");          // (5+3)/2
        assertThat(clientExp.getVerifiedMeanScore()).isEqualByComparingTo("5.00");  // verified only
        assertThat(clientExp.getDistribution()).contains("\"5\":1").contains("\"3\":1");
    }

    @Test
    void recomputeIsIdempotent() {
        UUID tenant = UUID.randomUUID();
        String provider = "PROVAGG9999999999999999999";
        submitPublished(tenant, provider, null, new BigDecimal("4.0"));

        aggregationService.recomputeForProvider(tenant, provider, "2026-07");
        int before = summaryRepository.findByTenantIdAndProviderPublicId(tenant, provider).size();
        aggregationService.recomputeForProvider(tenant, provider, "2026-07");
        int after = summaryRepository.findByTenantIdAndProviderPublicId(tenant, provider).size();
        assertThat(after).isEqualTo(before); // upsert, not duplicate
    }
}
