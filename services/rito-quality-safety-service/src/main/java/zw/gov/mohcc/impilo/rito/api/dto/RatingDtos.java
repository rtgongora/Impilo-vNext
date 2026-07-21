package zw.gov.mohcc.impilo.rito.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

    // ── Read surfaces (RW5) ──────────────────────────────────────────────────

    /** Public per-domain summary — verified fields only (no respondent identity). */
    public record PublicDomainSummary(String domain, BigDecimal verifiedMeanScore,
                                      int verifiedCount, String distribution) {}

    /**
     * Public verified-experience summary for a provider profile. Shows only the verified
     * experience domains; source-tagged "Rito". VARAPI/TUSO compose this read-only.
     */
    public record ExperienceSummaryResponse(String providerPublicId, String reportingPeriod,
                                            String source, int verifiedInteractionTotal,
                                            List<PublicDomainSummary> domains) {}

    /** Authorised management per-domain summary — verified AND overall (all four domains). */
    public record ManagementDomainSummary(String domain, BigDecimal meanScore, int ratingCount,
                                          BigDecimal verifiedMeanScore, int verifiedCount, String distribution) {}

    /** Authorised management view — all domains + moderation counts (TSHEPO-gated lane). */
    public record ManagementViewResponse(String providerPublicId, String reportingPeriod,
                                         List<ManagementDomainSummary> domains,
                                         Map<String, Long> moderationStateCounts) {}

    /** Public facility experience summary (RW8) — verified experience domains, source-tagged. */
    public record FacilityExperienceSummaryResponse(UUID facilityId, String reportingPeriod,
                                                    String source, int verifiedInteractionTotal,
                                                    List<PublicDomainSummary> domains) {}
}
