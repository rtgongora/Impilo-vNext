package zw.gov.mohcc.impilo.simba.social.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.ChallengeEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.ChallengeParticipantEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.ChallengeParticipantRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.ChallengeRepository;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Social extension of the existing {@link zw.gov.mohcc.impilo.simba.core.ChallengeService}. Adds
 * share-to-feed (a participant can publish a SANITISED social post celebrating their progress — reuses
 * {@link SocialPostService} + {@link MilestoneSanitiser}, never emitting clinical values), a ranked
 * group leaderboard, and a programme-campaign aggregate (aggregate-only, no per-person content).
 *
 * <p>The underlying {@code challenges}/{@code challenge_participants} tables are REUSED & EXTENDED
 * (V012); no parallel table.
 */
@Service
public class SocialChallengeService {

    private final ChallengeRepository challenges;
    private final ChallengeParticipantRepository participants;
    private final SocialPostService posts;

    public SocialChallengeService(ChallengeRepository challenges,
                                  ChallengeParticipantRepository participants,
                                  SocialPostService posts) {
        this.challenges = challenges;
        this.participants = participants;
        this.posts = posts;
    }

    public record LeaderboardRow(int rank, String personCpid, BigDecimal progressValue,
                                 boolean sharedToFeed) { }

    public record CampaignAggregate(UUID programmeId, long challengeCount, long campaignCount,
                                    long totalEnrolment, long totalCompletions,
                                    double completionRate) { }

    /**
     * Share a participant's challenge progress to the wellness-social feed. Creates a sanitised post
     * (milestone-safe: no clinical values) and stamps {@code share_post_id}/{@code shared_to_feed} on the
     * participant. Idempotent: a second share reuses the existing post.
     */
    @Transactional
    public SocialPostEntity shareToFeed(UUID tenantId, UUID challengeId, String personCpid,
                                        String message, String visibility,
                                        String correlationId, String requestId) {
        ChallengeEntity c = challenges.findByChallengeIdAndTenantId(challengeId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));
        ChallengeParticipantEntity p = participants
                .findByChallengeIdAndPersonCpid(challengeId, personCpid)
                .orElseThrow(() -> new IllegalArgumentException("You are not participating in this challenge"));

        // Default celebratory copy; the caller may override. Sanitisation happens inside SocialPostService.
        String body = (message == null || message.isBlank())
                ? "I'm taking part in the \"" + c.getTitle() + "\" challenge — join me!"
                : message;

        SocialPostService.CreatePost in = new SocialPostService.CreatePost(
                personCpid, "CLIENT", null, c.getGroupId(), c.getCommunityId(), c.getProgrammeId(),
                body, null, null, "challenge:" + challengeId,
                visibility == null || visibility.isBlank() ? "PUBLIC_WITHIN_IMPILO" : visibility);
        SocialPostEntity post = posts.create(tenantId, in, correlationId, requestId);

        p.setSharedToFeed(true);
        p.setSharePostId(post.getPostId());
        participants.save(p);
        return post;
    }

    /** Ranked leaderboard for a challenge (highest progress first); recomputes + persists each rank. */
    @Transactional
    public List<LeaderboardRow> groupLeaderboard(UUID tenantId, UUID challengeId) {
        challenges.findByChallengeIdAndTenantId(challengeId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));
        List<ChallengeParticipantEntity> rows =
                participants.findByChallengeIdOrderByProgressValueDesc(challengeId);
        List<LeaderboardRow> out = new ArrayList<>(rows.size());
        int rank = 1;
        for (ChallengeParticipantEntity p : rows) {
            p.setRank(rank);
            participants.save(p);
            out.add(new LeaderboardRow(rank, p.getPersonCpid(),
                    p.getProgressValue() == null ? BigDecimal.ZERO : p.getProgressValue(),
                    p.isSharedToFeed()));
            rank++;
        }
        return out;
    }

    /**
     * Programme-campaign aggregate — challenge/campaign counts, total enrolment, completions (progress
     * meeting the target), completion rate. Aggregate-only: NO per-person rows are returned.
     */
    @Transactional(readOnly = true)
    public CampaignAggregate campaignAggregate(UUID tenantId, UUID programmeId) {
        List<ChallengeEntity> programmeChallenges =
                challenges.findByTenantIdAndProgrammeIdOrderByCreatedAtDesc(tenantId, programmeId);
        long campaignCount = programmeChallenges.stream().filter(ChallengeEntity::isCampaignFlag).count();
        long totalEnrolment = 0;
        long totalCompletions = 0;
        for (ChallengeEntity c : programmeChallenges) {
            List<ChallengeParticipantEntity> ps =
                    participants.findByChallengeIdOrderByProgressValueDesc(c.getChallengeId());
            totalEnrolment += ps.size();
            BigDecimal target = c.getTargetValue();
            if (target != null && target.signum() > 0) {
                totalCompletions += ps.stream()
                        .filter(p -> p.getProgressValue() != null
                                && p.getProgressValue().compareTo(target) >= 0)
                        .count();
            }
        }
        double rate = totalEnrolment == 0 ? 0.0 : (double) totalCompletions / totalEnrolment;
        return new CampaignAggregate(programmeId, programmeChallenges.size(), campaignCount,
                totalEnrolment, totalCompletions, rate);
    }
}
