package zw.gov.mohcc.impilo.simba.social;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.simba.core.SimbaEventEmitter;
import zw.gov.mohcc.impilo.simba.core.WellnessAuditService;
import zw.gov.mohcc.impilo.simba.social.core.SocialCommunityService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialCommunityEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialCommunityMembershipEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;
import zw.gov.mohcc.impilo.simba.social.repository.SocialCommunityMembershipRepository;
import zw.gov.mohcc.impilo.simba.social.repository.SocialCommunityRepository;
import zw.gov.mohcc.impilo.simba.social.repository.SocialPostRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Community official-announcement emission + admin-role enforcement. */
class SocialCommunityServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000020");
    private static final UUID COMMUNITY = UUID.randomUUID();

    private final SocialCommunityRepository communities = mock(SocialCommunityRepository.class);
    private final SocialCommunityMembershipRepository members =
            mock(SocialCommunityMembershipRepository.class);
    private final SocialPostRepository posts = mock(SocialPostRepository.class);
    private final SimbaEventEmitter events = mock(SimbaEventEmitter.class);
    private final WellnessAuditService audit = mock(WellnessAuditService.class);

    private SocialCommunityService svc() {
        return new SocialCommunityService(communities, members, posts, events, audit);
    }

    private SocialCommunityMembershipEntity membership(String cpid, String role, String status) {
        SocialCommunityMembershipEntity m = new SocialCommunityMembershipEntity();
        m.setTenantId(TENANT);
        m.setCommunityId(COMMUNITY);
        m.setPersonCpid(cpid);
        m.setRole(role);
        m.setStatus(status);
        return m;
    }

    private SocialCommunityEntity community() {
        SocialCommunityEntity c = new SocialCommunityEntity();
        c.setTenantId(TENANT);
        c.setCommunityId(COMMUNITY);
        c.setName("Diabetes Wellness");
        return c;
    }

    @Test
    void officialAnnouncementEmitsEventAndCommunityScopedPost() {
        when(members.findByCommunityIdAndPersonCpid(COMMUNITY, "admin"))
                .thenReturn(Optional.of(membership("admin", "PROGRAMME_OFFICER", "ACTIVE")));
        when(communities.findByCommunityIdAndTenantId(COMMUNITY, TENANT))
                .thenReturn(Optional.of(community()));
        when(posts.save(any(SocialPostEntity.class))).thenAnswer(i -> {
            SocialPostEntity p = i.getArgument(0);
            if (p.getPostId() == null) p.setPostId(UUID.randomUUID());
            return p;
        });

        SocialPostEntity p = svc().announce(TENANT, COMMUNITY, "admin", "FACILITY_PROGRAMME",
                "Screening drive next week", "corr", "req");

        assertThat(p.isOfficial()).isTrue();
        assertThat(p.isPinned()).isTrue();
        assertThat(p.getVisibility()).isEqualTo("COMMUNITY");
        assertThat(p.getCommunityId()).isEqualTo(COMMUNITY);
        verify(events).emit(eq(TENANT), eq("SIMBA_GROUP_ANNOUNCEMENT_PUBLISHED"), anyString(),
                anyString(), eq("admin"), anyString(), any());
    }

    @Test
    void plainMemberCannotAnnounce() {
        when(members.findByCommunityIdAndPersonCpid(COMMUNITY, "member"))
                .thenReturn(Optional.of(membership("member", "MEMBER", "ACTIVE")));
        assertThatThrownBy(() -> svc().announce(TENANT, COMMUNITY, "member", "CITIZEN",
                "hi", "corr", "req"))
                .isInstanceOf(SocialCommunityService.CommunityAccessException.class);
        verify(events, never()).emit(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void joinIsImmediatelyActive() {
        when(communities.findByCommunityIdAndTenantId(COMMUNITY, TENANT))
                .thenReturn(Optional.of(community()));
        when(members.findByCommunityIdAndPersonCpid(COMMUNITY, "joiner")).thenReturn(Optional.empty());
        when(members.save(any(SocialCommunityMembershipEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(members.countByCommunityIdAndStatus(COMMUNITY, "ACTIVE")).thenReturn(1L);

        SocialCommunityMembershipEntity m = svc().join(TENANT, COMMUNITY, "joiner", "corr", "req");
        assertThat(m.getStatus()).isEqualTo("ACTIVE");
    }
}
