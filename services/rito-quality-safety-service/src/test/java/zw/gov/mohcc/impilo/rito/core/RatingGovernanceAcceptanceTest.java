package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.DomainScoreInput;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.SubmitRatingRequest;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderRatingEntity;

import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RW7 acceptance / verdict gate for the Experience & Reputation capability. Asserts the
 * load-bearing doctrine invariants: the regulation firewall (a rating write can never
 * touch provider authority), respondent-identity protection, and review-bombing detection.
 */
@SpringBootTest
@ActiveProfiles("test")
class RatingGovernanceAcceptanceTest {

    @Autowired private RatingService ratingService;
    @Autowired private RatingModerationService moderationService;

    private static SubmitRatingRequest req(String provider, boolean identityProtected, String actorRef) {
        return new SubmitRatingRequest(provider, null, null, null, "ATTENDING", null, null, "CLIENT",
                identityProtected, actorRef, "2026-07", new BigDecimal("2.0"), null,
                List.of(new DomainScoreInput("CLIENT_EXPERIENCE", "communication", new BigDecimal("2.0"), "1-5")));
    }

    /**
     * REGULATION FIREWALL: the rating pipeline depends only on Rito's own persistence +
     * event emitter — never on any Varapi / TSHEPO / Vashandi authority client — so a
     * rating write is structurally incapable of mutating licence, scope, employment or
     * access. Enforced here by reflecting on the service's collaborators.
     */
    @Test
    void ratingServiceHasNoProviderAuthorityDependency() {
        for (Parameter p : RatingService.class.getDeclaredConstructors()[0].getParameters()) {
            String type = p.getType().getName().toLowerCase();
            assertThat(type)
                    .as("RatingService collaborator %s must not be an external authority client", type)
                    .doesNotContain("varapi")
                    .doesNotContain("tshepo")
                    .doesNotContain("vashandi")
                    .doesNotContain("authoriz")
                    .doesNotContain("privilege")
                    .doesNotContain("licence")
                    .doesNotContain("scope");
        }
    }

    /** Respondent identity is retained only when the respondent is NOT identity-protected. */
    @Test
    void respondentIdentityIsShieldedWhenProtected() {
        UUID tenant = UUID.randomUUID();
        ProviderRatingEntity protectedRating =
                ratingService.submitRating(tenant, req("PROVGOV1111111111111111111", true, "actor-secret"));
        assertThat(protectedRating.getRespondentActorRef()).isNull();

        ProviderRatingEntity openRating =
                ratingService.submitRating(tenant, req("PROVGOV2222222222222222222", false, "actor-open"));
        assertThat(openRating.getRespondentActorRef()).isEqualTo("actor-open");
    }

    /** Review-bombing: a cluster of unverified ratings is flagged for a human (never auto-actioned). */
    @Test
    void reviewBombingIsFlaggedForReview() {
        UUID tenant = UUID.randomUUID();
        String provider = "PROVGOV3333333333333333333";
        ProviderRatingEntity last = null;
        for (int i = 0; i < 12; i++) {
            last = ratingService.submitRating(tenant, req(provider, true, null));
        }
        assertThat(moderationService.detectManipulation(tenant, last))
                .contains("REVIEW_BOMBING_SUSPECTED");
    }
}
