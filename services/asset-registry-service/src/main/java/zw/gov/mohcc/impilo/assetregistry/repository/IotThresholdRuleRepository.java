package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.IotThresholdRuleEntity;

import java.util.List;
import java.util.UUID;

public interface IotThresholdRuleRepository extends JpaRepository<IotThresholdRuleEntity, UUID> {

    List<IotThresholdRuleEntity> findByTenantIdAndMetricTypeAndActiveTrue(UUID tenantId, String metricType);
}
