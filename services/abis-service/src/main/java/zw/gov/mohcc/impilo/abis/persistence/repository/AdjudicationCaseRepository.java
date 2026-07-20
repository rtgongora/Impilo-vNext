package zw.gov.mohcc.impilo.abis.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.abis.persistence.entity.AdjudicationCaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdjudicationCaseRepository extends JpaRepository<AdjudicationCaseEntity, Long> {

    Page<AdjudicationCaseEntity> findByTenantIdAndStatusOrderByCreatedAtAsc(
            UUID tenantId, String status, Pageable pageable);

    Page<AdjudicationCaseEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<AdjudicationCaseEntity> findByIdAndTenantId(Long id, UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    /** Case counts grouped by status for the stats dashboard. */
    @Query("""
            SELECT c.status, COUNT(c)
            FROM AdjudicationCaseEntity c
            WHERE c.tenantId = :tenantId
            GROUP BY c.status
            """)
    List<Object[]> countByStatus(@Param("tenantId") UUID tenantId);
}
