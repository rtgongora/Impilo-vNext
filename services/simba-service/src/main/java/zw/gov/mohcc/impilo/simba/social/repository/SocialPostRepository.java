package zw.gov.mohcc.impilo.simba.social.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialPostRepository extends JpaRepository<SocialPostEntity, Long> {

    Optional<SocialPostEntity> findByPostIdAndTenantId(UUID postId, UUID tenantId);

    /** Visible posts newest-first with keyset by id; cursor null → first page. */
    @Query("""
            SELECT p FROM SocialPostEntity p
            WHERE p.tenantId = :tenantId AND p.moderationStatus = 'VISIBLE'
              AND (:cursor IS NULL OR p.id < :cursor)
            ORDER BY p.id DESC
            """)
    List<SocialPostEntity> feed(@Param("tenantId") UUID tenantId,
                                @Param("cursor") Long cursor,
                                Pageable pageable);

    @Query("""
            SELECT p FROM SocialPostEntity p
            WHERE p.tenantId = :tenantId AND p.actorCpid = :cpid AND p.moderationStatus = 'VISIBLE'
              AND (:cursor IS NULL OR p.id < :cursor)
            ORDER BY p.id DESC
            """)
    List<SocialPostEntity> myActivity(@Param("tenantId") UUID tenantId,
                                      @Param("cpid") String cpid,
                                      @Param("cursor") Long cursor,
                                      Pageable pageable);

    @Query("""
            SELECT p FROM SocialPostEntity p
            WHERE p.tenantId = :tenantId AND p.groupId = :groupId AND p.moderationStatus = 'VISIBLE'
              AND (:cursor IS NULL OR p.id < :cursor)
            ORDER BY p.id DESC
            """)
    List<SocialPostEntity> byGroup(@Param("tenantId") UUID tenantId,
                                   @Param("groupId") UUID groupId,
                                   @Param("cursor") Long cursor,
                                   Pageable pageable);
}
