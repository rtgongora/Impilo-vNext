package zw.gov.mohcc.impilo.community.social.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialCommunityEntity;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialCommunityMembershipEntity;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialGroupMembershipEntity;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialPageEntity;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialPostEntity;
import zw.gov.mohcc.impilo.community.social.persistence.repository.SocialBookmarkRepository;
import zw.gov.mohcc.impilo.community.social.persistence.repository.SocialCommentRepository;
import zw.gov.mohcc.impilo.community.social.persistence.repository.SocialCommunityMembershipRepository;
import zw.gov.mohcc.impilo.community.social.persistence.repository.SocialCommunityRepository;
import zw.gov.mohcc.impilo.community.social.persistence.repository.SocialGroupMembershipRepository;
import zw.gov.mohcc.impilo.community.social.persistence.repository.SocialGroupRepository;
import zw.gov.mohcc.impilo.community.social.persistence.repository.SocialModerationCaseRepository;
import zw.gov.mohcc.impilo.community.social.persistence.repository.SocialPageFollowerRepository;
import zw.gov.mohcc.impilo.community.social.persistence.repository.SocialPageRepository;
import zw.gov.mohcc.impilo.community.social.persistence.repository.SocialPostRepository;
import zw.gov.mohcc.impilo.community.social.persistence.repository.SocialReactionRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Block 3B — proves that pinning a post is gated by resource-level moderation
 * authority (community moderator / group moderator / page admin), and that
 * unauthorized actors are rejected with 403. Replaces the prior unguarded pin.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SocialServicePinAuthzTest {

    @Mock private SocialPostRepository postRepo;
    @Mock private SocialCommentRepository commentRepo;
    @Mock private SocialReactionRepository reactionRepo;
    @Mock private SocialBookmarkRepository bookmarkRepo;
    @Mock private SocialCommunityRepository communityRepo;
    @Mock private SocialCommunityMembershipRepository communityMembershipRepo;
    @Mock private SocialGroupRepository groupRepo;
    @Mock private SocialGroupMembershipRepository groupMembershipRepo;
    @Mock private SocialPageRepository pageRepo;
    @Mock private SocialPageFollowerRepository pageFollowerRepo;
    @Mock private SocialModerationCaseRepository moderationRepo;
    @Mock private SocialEventEmitter events;

    private SocialService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new SocialService(postRepo, commentRepo, reactionRepo, bookmarkRepo,
                communityRepo, communityMembershipRepo, groupRepo, groupMembershipRepo,
                pageRepo, pageFollowerRepo, moderationRepo, events, new ObjectMapper());
        tenantId = UUID.randomUUID();
    }

    private SocialPostEntity persistedPost() {
        SocialPostEntity p = new SocialPostEntity();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setAuthorActorId("author-1");
        when(postRepo.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private void assertForbidden(SocialPostEntity post, String actorId) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.pin(tenantId, actorId, post.getId(), true));
        assertEquals(403, ex.getStatusCode().value());
        verify(postRepo, never()).save(any());
        verify(events, never()).emit(any(), any(), any(), any(), any());
    }

    // ── community posts ──────────────────────────────────────────────────────

    @Test
    void communityModeratorInList_canPin() {
        SocialPostEntity post = persistedPost();
        UUID communityId = UUID.randomUUID();
        post.setCommunityId(communityId);
        SocialCommunityEntity c = new SocialCommunityEntity();
        c.setModeratorIdsJson("[\"mod-1\"]");
        when(communityRepo.findById(communityId)).thenReturn(Optional.of(c));

        service.pin(tenantId, "mod-1", post.getId(), true);

        assertEquals(true, post.isPinned());
        verify(postRepo, times(1)).save(post);
        verify(events, times(1)).emit(eq(tenantId), eq("post.pinned"), any(), any(), any());
    }

    @Test
    void communityMemberWithModeratorRole_canPin() {
        SocialPostEntity post = persistedPost();
        UUID communityId = UUID.randomUUID();
        post.setCommunityId(communityId);
        when(communityRepo.findById(communityId)).thenReturn(Optional.of(new SocialCommunityEntity()));
        SocialCommunityMembershipEntity m = new SocialCommunityMembershipEntity();
        m.setActorId("mod-2");
        m.setRole("moderator");
        when(communityMembershipRepo.findFirstByCommunityIdAndActorId(communityId, "mod-2"))
                .thenReturn(Optional.of(m));

        service.pin(tenantId, "mod-2", post.getId(), true);

        verify(postRepo, times(1)).save(post);
    }

    @Test
    void communityPlainMember_isForbidden() {
        SocialPostEntity post = persistedPost();
        UUID communityId = UUID.randomUUID();
        post.setCommunityId(communityId);
        when(communityRepo.findById(communityId)).thenReturn(Optional.of(new SocialCommunityEntity()));
        SocialCommunityMembershipEntity m = new SocialCommunityMembershipEntity();
        m.setRole("member");
        when(communityMembershipRepo.findFirstByCommunityIdAndActorId(communityId, "rando"))
                .thenReturn(Optional.of(m));

        assertForbidden(post, "rando");
    }

    // ── group posts ────────────────────────────────────────────────────────

    // ── group posts ────────────────────────────────────────────────────────

    @Test
    void groupModerator_canPin() {
        SocialPostEntity post = persistedPost();
        UUID groupId = UUID.randomUUID();
        post.setGroupId(groupId);
        SocialGroupMembershipEntity mod = new SocialGroupMembershipEntity();
        mod.setRole("owner");
        when(groupMembershipRepo.findFirstByGroupIdAndActorId(groupId, "g-owner"))
                .thenReturn(Optional.of(mod));

        service.pin(tenantId, "g-owner", post.getId(), true);
        verify(postRepo, times(1)).save(post);
    }

    @Test
    void groupPlainMember_isForbidden() {
        SocialPostEntity post = persistedPost();
        UUID groupId = UUID.randomUUID();
        post.setGroupId(groupId);
        SocialGroupMembershipEntity member = new SocialGroupMembershipEntity();
        member.setRole("member");
        when(groupMembershipRepo.findFirstByGroupIdAndActorId(groupId, "g-member"))
                .thenReturn(Optional.of(member));
        assertForbidden(post, "g-member");
    }

    // ── page posts ───────────────────────────────────────────────────────────

    @Test
    void pageAdmin_canPin() {
        SocialPostEntity post = persistedPost();
        UUID pageId = UUID.randomUUID();
        post.setPageId(pageId);
        SocialPageEntity page = new SocialPageEntity();
        page.setAdminIdsJson("[\"page-admin\"]");
        when(pageRepo.findById(pageId)).thenReturn(Optional.of(page));

        service.pin(tenantId, "page-admin", post.getId(), false);
        verify(postRepo, times(1)).save(post);
    }

    @Test
    void pageNonAdmin_isForbidden() {
        SocialPostEntity post = persistedPost();
        UUID pageId = UUID.randomUUID();
        post.setPageId(pageId);
        SocialPageEntity page = new SocialPageEntity();
        page.setAdminIdsJson("[\"page-admin\"]");
        when(pageRepo.findById(pageId)).thenReturn(Optional.of(page));
        assertForbidden(post, "not-admin");
    }

    // ── personal (uncontained) posts ─────────────────────────────────────────

    @Test
    void containerlessPost_authorCanPin() {
        SocialPostEntity post = persistedPost(); // author-1, no community/group/page
        service.pin(tenantId, "author-1", post.getId(), true);
        verify(postRepo, times(1)).save(post);
    }

    @Test
    void containerlessPost_nonAuthor_isForbidden() {
        SocialPostEntity post = persistedPost(); // author-1
        assertForbidden(post, "someone-else");
    }

    @Test
    void nullActor_isForbidden() {
        SocialPostEntity post = persistedPost();
        post.setCommunityId(UUID.randomUUID());
        assertForbidden(post, null);
    }
}
