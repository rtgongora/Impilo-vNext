package zw.gov.mohcc.impilo.surv.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surv.persistence.entity.AlertDefinitionEntity;

import java.util.List;
import java.util.UUID;

public interface AlertDefinitionRepository extends JpaRepository<AlertDefinitionEntity, Long> {
    List<AlertDefinitionEntity> findByTenantIdAndActiveTrue(UUID tenantId);
    List<AlertDefinitionEntity> findByTenantIdAndSyndromeCodeAndActiveTrue(UUID tenantId, String syndromeCode);
}
