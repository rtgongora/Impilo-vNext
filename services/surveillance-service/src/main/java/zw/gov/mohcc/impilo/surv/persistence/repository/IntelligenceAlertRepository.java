package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surv.persistence.entity.IntelligenceAlertEntity;

public interface IntelligenceAlertRepository extends JpaRepository<IntelligenceAlertEntity, Long> {
    List<IntelligenceAlertEntity> findByTenantIdOrderByIdDesc(UUID tenantId);
    List<IntelligenceAlertEntity> findByTenantIdAndStatusOrderByIdDesc(UUID tenantId, String status);
}
