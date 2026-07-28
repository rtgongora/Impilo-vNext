package zw.gov.mohcc.impilo.surgery.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surgery.persistence.entity.AnalyticsIndicatorDefinitionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalyticsIndicatorDefinitionRepository extends JpaRepository<AnalyticsIndicatorDefinitionEntity, UUID> {
    List<AnalyticsIndicatorDefinitionEntity> findByTenantIdOrderByDisplayOrderAsc(UUID tenantId);

    Optional<AnalyticsIndicatorDefinitionEntity> findByTenantIdAndIndicatorCode(UUID tenantId, String indicatorCode);
}
