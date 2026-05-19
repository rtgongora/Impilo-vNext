package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.surv.core.InvestigationStatus;
import zw.gov.mohcc.impilo.surv.persistence.entity.InvestigationEntity;

@Repository
public interface InvestigationRepository extends JpaRepository<InvestigationEntity, Long> {

    List<InvestigationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, InvestigationStatus status);

    long countByTenantIdAndStatusNot(UUID tenantId, InvestigationStatus status);
}
