package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surv.persistence.entity.PhDataQualityIssueEntity;

public interface PhDataQualityIssueRepository extends JpaRepository<PhDataQualityIssueEntity, Long> {
    List<PhDataQualityIssueEntity> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
    long countByTenantIdAndStatusIgnoreCase(UUID tenantId, String status);
}
