package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.DomainScoreInput;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.ModerationRequest;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.SubmitRatingRequest;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderRatingEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.RatingModerationEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.ProviderRatingRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.RatingDomainScoreRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RatingServiceTest {

    @Autowired private RatingService ratingService;
    @Autowired private RatingModerationService moderationService;
    @Autowired private VerifiedInteractionService verifiedInteractionService;
    @Autowired private ProviderRatingRepository ratingRepository;
    @Autowired private RatingDomainScoreRepository domainScoreRepository;

    private static SubmitRatingRequest request(String provider, String encounterRef) {
        return new SubmitRatingRequest(provider, null, null, encounterRef, "ATTENDING", null, null,
                "CLIENT", true, null, null, new BigDecimal("4.6"), null,
                List.of(new DomainScoreInput("CLIENT_EXPERIENCE", "communication", new BigDecimal("4.7"), "1-5"),
                        new DomainScoreInput("ACCESS_PROCESS", "waiting_experience", new BigDecimal("4.1"), "1-5")));
    }

    @Test
    void verifiedRatingIsStampedAndDomainsStoredSeparately() {
        UUID tenant = UUID.randomUUID();
        String provider = "PROVXXXXXXXXXXXXXXXXXXXXXX1";
        String encounterRef = UUID.randomUUID().toString();

        Map<String, Object> pct = new HashMap<>();
        pct.put("encounterRef", encounterRef);
        pct.put("attendingProviderId", provider);
        pct.put("modality", "PHYSICAL");
        verifiedInteractionService.recordFromPct(tenant, pct);

        ProviderRatingEntity rating = ratingService.submitRating(tenant, request(provider, encounterRef));
        assertThat(rating.getVerifiedInteraction()).isTrue();
        assertThat(rating.getVerificationSource()).isEqualTo("PCT");
        assertThat(rating.getModality()).isEqualTo("PHYSICAL");   // inherited from the verified interaction
        assertThat(domainScoreRepository.findByRatingId(rating.getId())).hasSize(2);

        // One verified rating per (encounter, provider).
        assertThatThrownBy(() -> ratingService.submitRating(tenant, request(provider, encounterRef)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unverifiedRatingIsAcceptedButFlaggedUnderReview() {
        UUID tenant = UUID.randomUUID();
        ProviderRatingEntity rating = ratingService.submitRating(tenant,
                request("PROVYYYYYYYYYYYYYYYYYYYYYY2", null));
        assertThat(rating.getVerifiedInteraction()).isFalse();
        assertThat(rating.getModerationState()).isEqualTo("SUBMITTED");
    }

    @Test
    void moderationPublishesAndUpdatesRatingState() {
        UUID tenant = UUID.randomUUID();
        ProviderRatingEntity rating = ratingService.submitRating(tenant,
                request("PROVZZZZZZZZZZZZZZZZZZZZZZ3", null));

        RatingModerationEntity mod = moderationService.moderate(tenant, "reviewer-1", rating.getId(),
                "PUBLISH", "meets criteria", null);
        assertThat(mod.getState()).isEqualTo("PUBLISHED");
        assertThat(ratingRepository.findById(rating.getId()).orElseThrow().getModerationState())
                .isEqualTo("PUBLISHED");
    }
}
