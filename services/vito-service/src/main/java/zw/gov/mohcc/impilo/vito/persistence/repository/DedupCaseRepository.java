package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.DedupCaseEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface DedupCaseRepository extends JpaRepository<DedupCaseEntity, Long> {

    Page<DedupCaseEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    @Query("""
            select dc
            from DedupCaseEntity dc
            where dc.tenantId = :tenantId
              and (dc.candidateA = :healthId or dc.candidateB = :healthId)
            """)
    List<DedupCaseEntity> findByTenantIdAndCandidate(@Param("tenantId") UUID tenantId,
                                                     @Param("healthId") UUID healthId);
}
