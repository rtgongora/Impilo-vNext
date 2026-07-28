package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
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

    /** Unpaged reads for the line list, which is an export of the whole cohort, not a screen page. */
    java.util.List<CaseEntity> findByTenantId(UUID tenantId);

    java.util.List<CaseEntity> findByTenantIdAndCaseType(UUID tenantId, String caseType);

    List<CaseEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Page<CaseEntity> findByTenantIdAndStatus(UUID tenantId, CaseStatus status, Pageable pageable);

    long countByTenantIdAndStatus(UUID tenantId, CaseStatus status);

    long countByTenantIdAndStatusNot(UUID tenantId, CaseStatus status);
}
