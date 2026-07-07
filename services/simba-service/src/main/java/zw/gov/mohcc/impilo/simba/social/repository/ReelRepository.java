package zw.gov.mohcc.impilo.simba.social.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.social.entity.ReelEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReelRepository extends JpaRepository<ReelEntity, Long> {

    Optional<ReelEntity> findByReelIdAndTenantId(UUID reelId, UUID tenantId);

    @Query("""
            SELECT r FROM ReelEntity r
            WHERE r.tenantId = :tenantId AND r.moderationStatus = 'VISIBLE'
              AND r.approvalStatus = 'APPROVED'
              AND (:cursor IS NULL OR r.id < :cursor)
            ORDER BY r.id DESC
            """)
    List<ReelEntity> feed(@Param("tenantId") UUID tenantId,
                          @Param("cursor") Long cursor,
                          Pageable pageable);
}
