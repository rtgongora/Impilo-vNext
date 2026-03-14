package zw.gov.mohcc.impilo.offlineedge.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.offlineedge.domain.ConflictReviewEntity;

import java.util.UUID;

@Repository
public interface ConflictReviewRepository extends JpaRepository<ConflictReviewEntity, UUID> {

    @Query("SELECT c FROM ConflictReviewEntity c WHERE c.tenantId = :tenantId " +
            "AND (:status IS NULL OR c.status = :status) ORDER BY c.createdAt DESC")
    Page<ConflictReviewEntity> findFiltered(UUID tenantId, String status, Pageable pageable);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
