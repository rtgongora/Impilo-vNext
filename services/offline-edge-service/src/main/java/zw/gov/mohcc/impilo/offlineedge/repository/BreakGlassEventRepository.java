package zw.gov.mohcc.impilo.offlineedge.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.offlineedge.domain.BreakGlassEventEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BreakGlassEventRepository extends JpaRepository<BreakGlassEventEntity, UUID> {

    @Query("SELECT e FROM BreakGlassEventEntity e WHERE e.tenantId = :tenantId " +
            "AND (:status IS NULL OR e.status = :status) ORDER BY e.activatedAt DESC")
    Page<BreakGlassEventEntity> findFiltered(UUID tenantId, String status, Pageable pageable);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    List<BreakGlassEventEntity> findByStatusAndReviewRequiredByBefore(String status, OffsetDateTime deadline);
}
