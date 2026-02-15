package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.surv.core.CaseStatus;
import zw.gov.mohcc.impilo.surv.persistence.entity.CaseEntity;

@Repository
public interface CaseRepository extends JpaRepository<CaseEntity, Long> {

    Page<CaseEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<CaseEntity> findByTenantIdAndStatus(UUID tenantId, CaseStatus status, Pageable pageable);
}
