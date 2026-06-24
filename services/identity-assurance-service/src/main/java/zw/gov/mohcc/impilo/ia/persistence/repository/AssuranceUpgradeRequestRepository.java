package zw.gov.mohcc.impilo.ia.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ia.core.UpgradeStatus;
import zw.gov.mohcc.impilo.ia.persistence.entity.AssuranceUpgradeRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssuranceUpgradeRequestRepository extends JpaRepository<AssuranceUpgradeRequestEntity, Long> {

    List<AssuranceUpgradeRequestEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, UpgradeStatus status);

    Optional<AssuranceUpgradeRequestEntity> findByIdAndTenantId(Long id, UUID tenantId);
}
