package zw.gov.mohcc.impilo.simba.social;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.simba.persistence.entity.ChallengeEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.ChallengeParticipantEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.ChallengeParticipantRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.ChallengeRepository;
import zw.gov.mohcc.impilo.simba.social.core.SocialChallengeService;
import zw.gov.mohcc.impilo.simba.social.core.SocialPostService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Share-to-feed sanitises, leaderboard ranks, campaign aggregate leaks no per-person rows. */
class SocialChallengeServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000030");
    private static final UUID CHALLENGE = UUID.randomUUID();
    private static final UUID PROGRAMME = UUID.randomUUID();

    private final ChallengeRepository challenges = mock(ChallengeRepository.class);
    private final ChallengeParticipantRepository participants = mock(ChallengeParticipantRepository.class);
    private final SocialPostService posts = mock(SocialPostService.class);

    private SocialChallengeService svc() {
        return new SocialChallengeService(challenges, participants, posts);
    }

    private ChallengeEntity challenge(BigDecimal target) {
        ChallengeEntity c = new ChallengeEntity();
        c.setTenantId(TENANT);
        c.setChallengeId(CHALLENGE);
        c.setTitle("10k steps");
        c.setTargetValue(target);
        c.setProgrammeId(PROGRAMME);
        return c;
    }

    private ChallengeParticipantEntity participant(String cpid, BigDecimal progress) {
        ChallengeParticipantEntity p = new ChallengeParticipantEntity();
        p.setTenantId(TENANT);
        p.setChallengeId(CHALLENGE);
        p.setPersonCpid(cpid);
        p.setProgressValue(progress);
        return p;
    }

    @Test
    void shareToFeedCreatesSanitisedPostAndStampsParticipant() {
        when(challenges.findByChallengeIdAndTenantId(CHALLENGE, TENANT))
                .thenReturn(Optional.of(challenge(new BigDecimal("100"))));
        when(participants.findByChallengeIdAndPersonCpid(CHALLENGE, "runner"))
                .thenReturn(Optional.of(participant("runner", new BigDecimal("50"))));
        UUID postId = UUID.randomUUID();
        when(posts.create(eq(TENANT), any(SocialPostService.CreatePost.class), anyString(), anyString()))
                .thenAnswer(i -> {
                    SocialPostEntity p = new SocialPostEntity();
                    p.setPostId(postId);
                    p.setActorCpid("runner");
                    return p;
                });
        when(participants.save(any(ChallengeParticipantEntity.class))).thenAnswer(i -> i.getArgument(0));

        SocialPostEntity post = svc().shareToFeed(TENANT, CHALLENGE, "runner",
                "Halfway to my goal!", "PUBLIC_WITHIN_IMPILO", "corr", "req");

        ArgumentCaptor<SocialPostService.CreatePost> captor =
                ArgumentCaptor.forClass(SocialPostService.CreatePost.class);
        verify(posts).create(eq(TENANT), captor.capture(), anyString(), anyString());
        assertThat(captor.getValue().milestoneRef()).isEqualTo("challenge:" + CHALLENGE);
        assertThat(post.getPostId()).isEqualTo(postId);

        ArgumentCaptor<ChallengeParticipantEntity> pcap =
                ArgumentCaptor.forClass(ChallengeParticipantEntity.class);
        verify(participants).save(pcap.capture());
        assertThat(pcap.getValue().isSharedToFeed()).isTrue();
        assertThat(pcap.getValue().getSharePostId()).isEqualTo(postId);
    }

    @Test
    void shareToFeedRejectsNonParticipant() {
        when(challenges.findByChallengeIdAndTenantId(CHALLENGE, TENANT))
                .thenReturn(Optional.of(challenge(new BigDecimal("100"))));
        when(participants.findByChallengeIdAndPersonCpid(CHALLENGE, "stranger"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc().shareToFeed(TENANT, CHALLENGE, "stranger", null, null, "c", "r"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leaderboardRanksByProgressDescending() {
        when(challenges.findByChallengeIdAndTenantId(CHALLENGE, TENANT))
                .thenReturn(Optional.of(challenge(new BigDecimal("100"))));
        when(participants.findByChallengeIdOrderByProgressValueDesc(CHALLENGE))
                .thenReturn(List.of(
                        participant("a", new BigDecimal("90")),
                        participant("b", new BigDecimal("60")),
                        participant("c", new BigDecimal("30"))));
        when(participants.save(any(ChallengeParticipantEntity.class))).thenAnswer(i -> i.getArgument(0));

        List<SocialChallengeService.LeaderboardRow> board = svc().groupLeaderboard(TENANT, CHALLENGE);
        assertThat(board).hasSize(3);
        assertThat(board.get(0).rank()).isEqualTo(1);
        assertThat(board.get(0).personCpid()).isEqualTo("a");
        assertThat(board.get(2).rank()).isEqualTo(3);
    }

    @Test
    void campaignAggregateCountsCompletionsWithoutPerPersonRows() {
        ChallengeEntity c1 = challenge(new BigDecimal("100"));
        c1.setCampaignFlag(true);
        when(challenges.findByTenantIdAndProgrammeIdOrderByCreatedAtDesc(TENANT, PROGRAMME))
                .thenReturn(List.of(c1));
        when(participants.findByChallengeIdOrderByProgressValueDesc(CHALLENGE))
                .thenReturn(List.of(
                        participant("a", new BigDecimal("100")),   // complete
                        participant("b", new BigDecimal("100")),   // complete
                        participant("c", new BigDecimal("40"))));  // incomplete

        SocialChallengeService.CampaignAggregate agg = svc().campaignAggregate(TENANT, PROGRAMME);
        assertThat(agg.challengeCount()).isEqualTo(1);
        assertThat(agg.campaignCount()).isEqualTo(1);
        assertThat(agg.totalEnrolment()).isEqualTo(3);
        assertThat(agg.totalCompletions()).isEqualTo(2);
        assertThat(agg.completionRate()).isEqualTo(2.0 / 3.0);
        // Aggregate record exposes only counts + rate — no cpid field exists on CampaignAggregate.
        assertThat(SocialChallengeService.CampaignAggregate.class.getRecordComponents())
                .noneMatch(rc -> rc.getName().toLowerCase().contains("cpid")
                        || rc.getName().toLowerCase().contains("person"));
    }
}
