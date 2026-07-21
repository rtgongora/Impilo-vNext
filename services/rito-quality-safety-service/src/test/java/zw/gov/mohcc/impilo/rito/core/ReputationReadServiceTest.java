package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.*;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderRatingEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReputationReadServiceTest {

    @Autowired private RatingService ratingService;
    @Autowired private RatingModerationService moderationService;
    @Autowired private VerifiedInteractionService verifiedInteractionService;
    @Autowired private ReputationAggregationService aggregationService;
    @Autowired private ReputationReadService readService;

    @Test
    void publicShowsExperienceDomainsOnly_managementShowsAll() {
        UUID tenant = UUID.randomUUID();
        String provider = "PROVREAD12345678901234567";
        String encounterRef = UUID.randomUUID().toString();

        Map<String, Object> pct = new HashMap<>();
        pct.put("encounterRef", encounterRef);
        pct.put("attendingProviderId", provider);
        verifiedInteractionService.recordFromPct(tenant, pct);

        // A verified rating scoring all four domains.
        var req = new SubmitRatingRequest(provider, null, null, encounterRef, "ATTENDING", null, null,
                "CLIENT", true, null, "2026-07", new BigDecimal("4.5"), null, List.of(
                new DomainScoreInput("CLIENT_EXPERIENCE", "communication", new BigDecimal("4.7"), "1-5"),
                new DomainScoreInput("ACCESS_PROCESS", "waiting_experience", new BigDecimal("4.1"), "1-5"),
                new DomainScoreInput("PROFESSIONAL_QUALITY", "documentation", new BigDecimal("4.0"), "1-5"),
                new DomainScoreInput("SAFETY_ACCOUNTABILITY", "incident_free", new BigDecimal("5.0"), "1-5")));
        ProviderRatingEntity r = ratingService.submitRating(tenant, req);
        moderationService.moderate(tenant, "reviewer", r.getId(), "PUBLISH", "ok", null);
        aggregationService.recomputeForProvider(tenant, provider, "2026-07");

        // PUBLIC: only the two experience domains, source-tagged, verified count present.
        ExperienceSummaryResponse pub = readService.publicExperienceSummary(tenant, provider, "2026-07");
        assertThat(pub.source()).isEqualTo("Rito");
        assertThat(pub.verifiedInteractionTotal()).isEqualTo(1);
        assertThat(pub.domains()).extracting(PublicDomainSummary::domain)
                .containsExactlyInAnyOrder("CLIENT_EXPERIENCE", "ACCESS_PROCESS");
        assertThat(pub.domains()).noneMatch(d -> d.domain().equals("PROFESSIONAL_QUALITY")
                || d.domain().equals("SAFETY_ACCOUNTABILITY"));

        // MANAGEMENT: all four domains + moderation counts.
        ManagementViewResponse mgmt = readService.managementView(tenant, provider, "2026-07");
        assertThat(mgmt.domains()).extracting(ManagementDomainSummary::domain)
                .contains("CLIENT_EXPERIENCE", "ACCESS_PROCESS", "PROFESSIONAL_QUALITY", "SAFETY_ACCOUNTABILITY");
        assertThat(mgmt.moderationStateCounts()).containsEntry("PUBLISHED", 1L);
    }
}
