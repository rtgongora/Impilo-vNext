package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ReconciliationCaseEntity;

import java.util.UUID;

@Repository
public interface ReconciliationCaseRepository extends JpaRepository<ReconciliationCaseEntity, Long> {

    Page<ReconciliationCaseEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Page<ReconciliationCaseEntity> findByCouncilIdAndStatus(Long councilId, String status, Pageable pageable);
}
