package zw.gov.mohcc.impilo.guidance.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.guidance.persistence.entity.ServiceAdvisoryEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceAdvisoryRepository extends JpaRepository<ServiceAdvisoryEntity, UUID> {

    Optional<ServiceAdvisoryEntity> findByIdAndTenantId(UUID id, String tenantId);

    List<ServiceAdvisoryEntity> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<ServiceAdvisoryEntity> findByTenantIdAndStatusOrderByUpdatedAtDesc(String tenantId, String status);

    /** Active read path: PUBLISHED advisories whose display window contains {@code now}. */
    @Query("SELECT a FROM ServiceAdvisoryEntity a WHERE a.tenantId = :tenantId AND a.status = 'PUBLISHED' "
            + "AND (a.startsAt IS NULL OR a.startsAt <= :now) "
            + "AND (a.expiresAt IS NULL OR a.expiresAt >= :now)")
    List<ServiceAdvisoryEntity> findActive(@Param("tenantId") String tenantId, @Param("now") OffsetDateTime now);

    /**
     * Public notices archive read path (V017): PUBLISHED, in-window notices, optionally
     * filtered by category, newest first. Audience targeting is matched in the service layer.
     */
    @Query("SELECT a FROM ServiceAdvisoryEntity a WHERE a.tenantId = :tenantId AND a.status = 'PUBLISHED' "
            + "AND (:category IS NULL OR a.category = :category) "
            + "AND (a.startsAt IS NULL OR a.startsAt <= :now) "
            + "AND (a.expiresAt IS NULL OR a.expiresAt >= :now) "
            + "ORDER BY a.startsAt DESC NULLS LAST, a.updatedAt DESC")
    List<ServiceAdvisoryEntity> findActiveNotices(@Param("tenantId") String tenantId,
                                                  @Param("category") String category,
                                                  @Param("now") OffsetDateTime now);
}
