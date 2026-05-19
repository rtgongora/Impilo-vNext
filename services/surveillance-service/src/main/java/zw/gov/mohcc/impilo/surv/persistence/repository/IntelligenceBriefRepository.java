package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.surv.core.BriefStatus;
import zw.gov.mohcc.impilo.surv.persistence.entity.IntelligenceBriefEntity;

@Repository
public interface IntelligenceBriefRepository extends JpaRepository<IntelligenceBriefEntity, Long> {

    List<IntelligenceBriefEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, BriefStatus status);
}
