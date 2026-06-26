package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.costa.domain.entity.EmergencyDeferredChargeEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ReconciliationState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyDeferredChargeRepository
        extends JpaRepository<EmergencyDeferredChargeEntity, UUID> {

    List<EmergencyDeferredChargeEntity> findTop100ByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<EmergencyDeferredChargeEntity> findTop100ByTenantIdAndReconciliationStateOrderByCreatedAtDesc(
            UUID tenantId, ReconciliationState reconciliationState);

    List<EmergencyDeferredChargeEntity> findTop100ByTenantIdAndProvisionalPersonRefOrderByCreatedAtDesc(
            UUID tenantId, String provisionalPersonRef);

    Optional<EmergencyDeferredChargeEntity> findByDeferredChargeIdAndTenantId(UUID id, UUID tenantId);
}
