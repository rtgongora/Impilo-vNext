package zw.gov.mohcc.impilo.simba.social;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.simba.core.SimbaEventEmitter;
import zw.gov.mohcc.impilo.simba.core.WellnessAuditService;
import zw.gov.mohcc.impilo.simba.social.core.SocialGroupService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialGroupEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialGroupMembershipEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;
import zw.gov.mohcc.impilo.simba.social.repository.SocialGroupMembershipRepository;
import zw.gov.mohcc.impilo.simba.social.repository.SocialGroupRepository;
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

/** Role lattice + posting/comment permission + announcement-event enforcement for social groups. */
class SocialGroupServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private static final UUID GROUP = UUID.randomUUID();

    private final SocialGroupRepository groups = mock(SocialGroupRepository.class);
    private final SocialGroupMembershipRepository members = mock(SocialGroupMembershipRepository.class);
    private final SocialPostRepository posts = mock(SocialPostRepository.class);
    private final SimbaEventEmitter events = mock(SimbaEventEmitter.class);
    private final WellnessAuditService audit = mock(WellnessAuditService.class);

    private SocialGroupService svc() {
        return new SocialGroupService(groups, members, posts, events, audit);
    }

    private SocialGroupMembershipEntity membership(String cpid, String role, String status) {
        SocialGroupMembershipEntity m = new SocialGroupMembershipEntity();
        m.setTenantId(TENANT);
        m.setGroupId(GROUP);
        m.setPersonCpid(cpid);
        m.setRole(role);
        m.setStatus(status);
        return m;
    }

    private SocialGroupEntity group() {
        SocialGroupEntity g = new SocialGroupEntity();
        g.setTenantId(TENANT);
        g.setGroupId(GROUP);
        g.setName("Walkers");
        return g;
    }

    // ── posting/comment permission ───────────────────────────────────────────────

    @Test
    void viewerCannotPostOrComment() {
        SocialGroupService s = svc();
        SocialGroupMembershipEntity viewer = membership("v", "VIEWER", "ACTIVE");
        assertThat(s.canPost(viewer)).isFalse();
        assertThat(s.canComment(viewer)).isFalse();
    }

    @Test
    void memberWithPermissionCanPost() {
        SocialGroupService s = svc();
        SocialGroupMembershipEntity member = membership("m", "MEMBER", "ACTIVE");
        assertThat(s.canPost(member)).isTrue();
        assertThat(s.canComment(member)).isTrue();
        assertThat(s.isOfficial(member)).isFalse();
    }

    @Test
    void perMembershipOverrideBlocksPosting() {
        SocialGroupService s = svc();
        SocialGroupMembershipEntity muted = membership("m", "MEMBER", "ACTIVE");
        muted.setCanPost(false);
        assertThat(s.canPost(muted)).isFalse();
    }

    @Test
    void officialRolesAreOfficial() {
        SocialGroupService s = svc();
        assertThat(s.isOfficial(membership("a", "ADMIN", "ACTIVE"))).isTrue();
        assertThat(s.isOfficial(membership("p", "VERIFIED_PROVIDER", "ACTIVE"))).isTrue();
        assertThat(s.isOfficial(membership("o", "PROGRAMME_OFFICER", "ACTIVE"))).isTrue();
    }

    @Test
    void enforceCanPostThrowsForNonMember() {
        when(members.findByGroupIdAndPersonCpid(GROUP, "stranger")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc().enforceCanPost(GROUP, "stranger"))
                .isInstanceOf(SocialGroupService.GroupAccessException.class);
    }

    @Test
    void enforceCanPostThrowsForViewer() {
        when(members.findByGroupIdAndPersonCpid(GROUP, "v"))
                .thenReturn(Optional.of(membership("v", "VIEWER", "ACTIVE")));
        assertThatThrownBy(() -> svc().enforceCanPost(GROUP, "v"))
                .isInstanceOf(SocialGroupService.GroupAccessException.class);
    }

    // ── announcements ────────────────────────────────────────────────────────────

    @Test
    void announcementByOfficialEmitsGroupAnnouncementEventAndPinnedOfficialPost() {
        when(members.findByGroupIdAndPersonCpid(GROUP, "admin"))
                .thenReturn(Optional.of(membership("admin", "ADMIN", "ACTIVE")));
        when(groups.findByGroupIdAndTenantId(GROUP, TENANT)).thenReturn(Optional.of(group()));
        when(posts.save(any(SocialPostEntity.class))).thenAnswer(i -> {
            SocialPostEntity p = i.getArgument(0);
            if (p.getPostId() == null) p.setPostId(UUID.randomUUID());
            return p;
        });

        SocialPostEntity p = svc().announce(TENANT, GROUP, "admin", "PROVIDER",
                "Group walk this Saturday 8am!", "corr", "req");

        assertThat(p.isPinned()).isTrue();
        assertThat(p.isOfficial()).isTrue();
        assertThat(p.getVisibility()).isEqualTo("GROUP");
        verify(events).emit(eq(TENANT), eq("SIMBA_GROUP_ANNOUNCEMENT_PUBLISHED"), anyString(),
                anyString(), eq("admin"), anyString(), any());
    }

    @Test
    void announcementByNonOfficialIsDenied() {
        when(members.findByGroupIdAndPersonCpid(GROUP, "member"))
                .thenReturn(Optional.of(membership("member", "MEMBER", "ACTIVE")));
        assertThatThrownBy(() -> svc().announce(TENANT, GROUP, "member", "CITIZEN",
                "hi", "corr", "req"))
                .isInstanceOf(SocialGroupService.GroupAccessException.class);
        verify(events, never()).emit(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void announcementWithClinicalValueIsRejected() {
        when(members.findByGroupIdAndPersonCpid(GROUP, "admin"))
                .thenReturn(Optional.of(membership("admin", "ADMIN", "ACTIVE")));
        when(groups.findByGroupIdAndTenantId(GROUP, TENANT)).thenReturn(Optional.of(group()));
        assertThatThrownBy(() -> svc().announce(TENANT, GROUP, "admin", "PROVIDER",
                "My blood pressure is 150/95 today", "corr", "req"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── membership approval ──────────────────────────────────────────────────────

    @Test
    void approveRequiresAdminAndActivatesRequestedMember() {
        when(members.findByGroupIdAndPersonCpid(GROUP, "owner"))
                .thenReturn(Optional.of(membership("owner", "OWNER", "ACTIVE")));
        when(members.findByGroupIdAndPersonCpid(GROUP, "pending"))
                .thenReturn(Optional.of(membership("pending", "MEMBER", "REQUESTED")));
        when(members.save(any(SocialGroupMembershipEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(groups.findByGroupIdAndTenantId(GROUP, TENANT)).thenReturn(Optional.of(group()));
        when(members.countByGroupIdAndStatus(GROUP, "ACTIVE")).thenReturn(2L);

        SocialGroupMembershipEntity m = svc().approve(TENANT, GROUP, "owner", "pending", "corr", "req");
        assertThat(m.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void approveByNonAdminIsDenied() {
        when(members.findByGroupIdAndPersonCpid(GROUP, "member"))
                .thenReturn(Optional.of(membership("member", "MEMBER", "ACTIVE")));
        assertThatThrownBy(() -> svc().approve(TENANT, GROUP, "member", "pending", "corr", "req"))
                .isInstanceOf(SocialGroupService.GroupAccessException.class);
    }

    @Test
    void nonOwnerCannotGrantOwnerRole() {
        when(members.findByGroupIdAndPersonCpid(GROUP, "admin"))
                .thenReturn(Optional.of(membership("admin", "ADMIN", "ACTIVE")));
        assertThatThrownBy(() -> svc().setRole(TENANT, GROUP, "admin", "target", "OWNER", "c", "r"))
                .isInstanceOf(SocialGroupService.GroupAccessException.class);
    }

    // ── join / request ───────────────────────────────────────────────────────────

    @Test
    void joinOpenGroupIsImmediatelyActive() {
        SocialGroupEntity g = group();
        g.setJoinPolicy("OPEN");
        when(groups.findByGroupIdAndTenantId(GROUP, TENANT)).thenReturn(Optional.of(g));
        when(members.findByGroupIdAndPersonCpid(GROUP, "joiner")).thenReturn(Optional.empty());
        when(members.save(any(SocialGroupMembershipEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(members.countByGroupIdAndStatus(GROUP, "ACTIVE")).thenReturn(1L);

        SocialGroupMembershipEntity m = svc().join(TENANT, GROUP, "joiner", "corr", "req");
        assertThat(m.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void joinRequestGroupIsPending() {
        SocialGroupEntity g = group();
        g.setJoinPolicy("REQUEST");
        when(groups.findByGroupIdAndTenantId(GROUP, TENANT)).thenReturn(Optional.of(g));
        when(members.findByGroupIdAndPersonCpid(GROUP, "joiner")).thenReturn(Optional.empty());
        when(members.save(any(SocialGroupMembershipEntity.class))).thenAnswer(i -> i.getArgument(0));

        SocialGroupMembershipEntity m = svc().join(TENANT, GROUP, "joiner", "corr", "req");
        assertThat(m.getStatus()).isEqualTo("REQUESTED");
    }

    @Test
    void joinClosedGroupIsDenied() {
        SocialGroupEntity g = group();
        g.setJoinPolicy("CLOSED");
        when(groups.findByGroupIdAndTenantId(GROUP, TENANT)).thenReturn(Optional.of(g));
        when(members.findByGroupIdAndPersonCpid(GROUP, "joiner")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc().join(TENANT, GROUP, "joiner", "corr", "req"))
                .isInstanceOf(SocialGroupService.GroupAccessException.class);
    }
}
