package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.core.MatchDisposition;
import zw.gov.mohcc.impilo.vito.persistence.entity.MatchResultEntity;

import java.util.UUID;

@Repository
public interface MatchResultRepository extends JpaRepository<MatchResultEntity, Long> {

    Page<MatchResultEntity> findByTenantIdAndDisposition(
            UUID tenantId, MatchDisposition disposition, Pageable pageable);

    Page<MatchResultEntity> findByTenantIdAndSourceHealthId(
            UUID tenantId, UUID sourceHealthId, Pageable pageable);

    Page<MatchResultEntity> findByTenantIdAndCandidateHealthId(
            UUID tenantId, UUID candidateHealthId, Pageable pageable);

    long countByTenantIdAndDisposition(UUID tenantId, MatchDisposition disposition);
}
