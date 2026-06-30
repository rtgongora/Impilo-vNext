package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.IotBreachStateEntity;

import java.util.Optional;
import java.util.UUID;

public interface IotBreachStateRepository extends JpaRepository<IotBreachStateEntity, UUID> {

    Optional<IotBreachStateEntity> findByTenantIdAndDeviceIdAndRuleId(UUID tenantId, String deviceId, UUID ruleId);
}
