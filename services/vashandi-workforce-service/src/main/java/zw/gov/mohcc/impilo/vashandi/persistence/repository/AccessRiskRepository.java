package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AccessRiskEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessRiskRepository extends JpaRepository<AccessRiskEntity, UUID> {

    List<AccessRiskEntity> findByTenantIdOrderByDetectedAtDesc(UUID tenantId);

    Optional<AccessRiskEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AccessRiskEntity> findByTenantIdAndStatusOrderBySeverityDescDetectedAtDesc(
            UUID tenantId, String status);

    List<AccessRiskEntity> findByTenantIdAndWorkforceProfileIdAndStatus(
            UUID tenantId, UUID workforceProfileId, String status);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    long countByTenantIdAndSeverity(UUID tenantId, String severity);
}
