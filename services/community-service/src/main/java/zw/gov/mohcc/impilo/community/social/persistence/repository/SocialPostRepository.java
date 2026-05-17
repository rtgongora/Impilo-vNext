package zw.gov.mohcc.impilo.community.social.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialPostEntity;

import java.util.List;
import java.util.UUID;

public interface SocialPostRepository extends JpaRepository<SocialPostEntity, UUID> {

    Page<SocialPostEntity> findByTenantIdAndStatusOrderByPublishedAtDesc(UUID tenantId, String status, Pageable pageable);

    Page<SocialPostEntity> findByTenantIdAndCommunityIdAndStatusOrderByPublishedAtDesc(
            UUID tenantId, UUID communityId, String status, Pageable pageable);

    Page<SocialPostEntity> findByTenantIdAndGroupIdAndStatusOrderByPublishedAtDesc(
            UUID tenantId, UUID groupId, String status, Pageable pageable);

    Page<SocialPostEntity> findByTenantIdAndPageIdAndStatusOrderByPublishedAtDesc(
            UUID tenantId, UUID pageId, String status, Pageable pageable);

    Page<SocialPostEntity> findByTenantIdAndAuthorActorIdOrderByCreatedAtDesc(
            UUID tenantId, String authorActorId, Pageable pageable);

    @Query("SELECT p FROM SocialPostEntity p WHERE p.tenantId = :tenantId AND p.kind = 'announcement' AND p.status = 'published' ORDER BY p.publishedAt DESC")
    Page<SocialPostEntity> findAnnouncements(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query(value = """
            SELECT p.* FROM community.social_posts p
            JOIN community.social_bookmarks b ON b.post_id = p.id
            WHERE b.tenant_id = :tenantId AND b.actor_id = :actorId
            ORDER BY b.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM community.social_bookmarks b
            WHERE b.tenant_id = :tenantId AND b.actor_id = :actorId
            """,
            nativeQuery = true)
    Page<SocialPostEntity> findBookmarkedByActor(@Param("tenantId") UUID tenantId,
                                                 @Param("actorId") String actorId,
                                                 Pageable pageable);

    @Query(value = """
            SELECT DISTINCT p.* FROM community.social_posts p
            WHERE p.tenant_id = :tenantId
              AND p.status = 'published'
              AND (
                p.visibility = 'public'
                OR (p.visibility = 'page_followers' AND p.page_id IN (
                      SELECT page_id FROM community.social_page_followers f WHERE f.actor_id = :actorId))
                OR (p.visibility = 'community' AND p.community_id IN (
                      SELECT community_id FROM community.social_community_memberships m WHERE m.actor_id = :actorId))
                OR (p.author_actor_id = :actorId)
              )
            ORDER BY p.pinned DESC, p.published_at DESC
            """,
            nativeQuery = true)
    Page<SocialPostEntity> findHomeFeedForActor(@Param("tenantId") UUID tenantId,
                                                @Param("actorId") String actorId,
                                                Pageable pageable);

    List<SocialPostEntity> findByTenantIdAndPageIdInAndStatusOrderByPublishedAtDesc(UUID tenantId, List<UUID> pageIds, String status);
}
