package zw.gov.mohcc.impilo.guidance.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.guidance.persistence.entity.GatewayEscalationExplainerEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatewayEscalationExplainerRepository extends JpaRepository<GatewayEscalationExplainerEntity, UUID> {

    Optional<GatewayEscalationExplainerEntity> findByTenantIdAndStepKeyAndStatus(String tenantId, String stepKey, String status);

    List<GatewayEscalationExplainerEntity> findByTenantIdAndStatusOrderByStepKeyAsc(String tenantId, String status);
}
