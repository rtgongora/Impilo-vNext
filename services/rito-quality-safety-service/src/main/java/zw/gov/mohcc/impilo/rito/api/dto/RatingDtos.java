package zw.gov.mohcc.impilo.rito.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request/response DTOs for the Rito Experience & Reputation capability (RW3).
 */
public final class RatingDtos {

    private RatingDtos() {}

    /** One measure's score within one of the four non-blendable domains. */
    public record DomainScoreInput(String domain, String measure, BigDecimal score, String scale) {}

    /**
     * Submit a provider rating. When {@code encounterRef} matches a recorded PCT
     * verified interaction the rating is stamped verified; otherwise it is accepted as
     * unverified public feedback (labelled, excluded from verified summaries).
     */
    public record SubmitRatingRequest(
            String providerPublicId,
            UUID facilityId,
            UUID servicePointId,
            String encounterRef,
            String providerRole,
            String modality,
            String specialty,
            String respondentClass,
            Boolean respondentIdentityProtected,
            String respondentActorRef,
            String reportingPeriod,
            BigDecimal overallScore,
            UUID narrativeCaseId,
            List<DomainScoreInput> domainScores) {}

    /** A moderation decision on a rating (reviewer role, RW3). */
    public record ModerationRequest(String decision, String reason, List<String> manipulationFlags) {}

    /** A provider's response to a rating. */
    public record ProviderResponseRequest(String providerPublicId, String responseText, String respondedBy) {}
}
