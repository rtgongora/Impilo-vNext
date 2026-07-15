package zw.gov.mohcc.impilo.simba.social.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.social.entity.SocialStatusEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialStatusRepository extends JpaRepository<SocialStatusEntity, Long> {

    Optional<SocialStatusEntity> findByStatusIdAndTenantId(UUID statusId, UUID tenantId);

    /** Active (VISIBLE, not-yet-expired) statuses newest-first; visibility is filtered by the service. */
    @Query("""
            SELECT s FROM SocialStatusEntity s
            WHERE s.tenantId = :tenantId AND s.moderationStatus = 'VISIBLE'
              AND (s.expiresAt IS NULL OR s.expiresAt > :now)
            ORDER BY s.id DESC
            """)
    List<SocialStatusEntity> findActive(@Param("tenantId") UUID tenantId,
                                        @Param("now") OffsetDateTime now,
                                        Pageable pageable);

    /** VISIBLE statuses whose expiry has passed — the sweep flips these to HIDDEN. */
    @Query("""
            SELECT s FROM SocialStatusEntity s
            WHERE s.moderationStatus = 'VISIBLE'
              AND s.expiresAt IS NOT NULL AND s.expiresAt <= :now
            ORDER BY s.id ASC
            """)
    List<SocialStatusEntity> findExpiredVisible(@Param("now") OffsetDateTime now, Pageable pageable);
}
