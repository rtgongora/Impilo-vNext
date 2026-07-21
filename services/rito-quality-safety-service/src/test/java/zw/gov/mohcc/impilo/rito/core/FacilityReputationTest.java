package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.DomainScoreInput;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.FacilityExperienceSummaryResponse;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.SubmitRatingRequest;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderRatingEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FacilityReputationTest {

    @Autowired private RatingService ratingService;
    @Autowired private RatingModerationService moderationService;
    @Autowired private VerifiedInteractionService verifiedInteractionService;
    @Autowired private ReputationAggregationService aggregationService;
    @Autowired private ReputationReadService readService;

    private void publish(UUID tenant, String provider, UUID facility, BigDecimal score) {
        String encounterRef = UUID.randomUUID().toString();
        Map<String, Object> pct = new HashMap<>();
        pct.put("encounterRef", encounterRef);
        pct.put("attendingProviderId", provider);
        pct.put("facilityId", facility.toString());
        verifiedInteractionService.recordFromPct(tenant, pct);

        var req = new SubmitRatingRequest(provider, facility, null, encounterRef, "ATTENDING", null, null,
                "CLIENT", true, null, "2026-07", score, null,
                List.of(new DomainScoreInput("CLIENT_EXPERIENCE", "communication", score, "1-5")));
        ProviderRatingEntity r = ratingService.submitRating(tenant, req);
        moderationService.moderate(tenant, "reviewer", r.getId(), "PUBLISH", "ok", null);
    }

    @Test
    void facilityReputationAggregatesAllProvidersAtTheFacility() {
        UUID tenant = UUID.randomUUID();
        UUID facility = UUID.randomUUID();
        publish(tenant, "PROVFAC111111111111111111A", facility, new BigDecimal("4.0"));
        publish(tenant, "PROVFAC222222222222222222B", facility, new BigDecimal("5.0"));

        int domains = aggregationService.recomputeForFacility(tenant, facility, "2026-07");
        assertThat(domains).isEqualTo(1); // CLIENT_EXPERIENCE

        FacilityExperienceSummaryResponse summary =
                readService.publicFacilityExperienceSummary(tenant, facility, "2026-07");
        assertThat(summary.source()).isEqualTo("Rito");
        assertThat(summary.facilityId()).isEqualTo(facility);
        assertThat(summary.domains()).hasSize(1);
        assertThat(summary.domains().get(0).domain()).isEqualTo("CLIENT_EXPERIENCE");
        // Two providers at the facility → mean (4+5)/2 = 4.5.
        assertThat(summary.domains().get(0).verifiedMeanScore()).isEqualByComparingTo("4.50");
    }
}
