package zw.gov.mohcc.impilo.datagovernance.deid.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidReleasePolicyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeidReleasePolicyRepository extends JpaRepository<DeidReleasePolicyEntity, UUID> {

    List<DeidReleasePolicyEntity> findByTenantId(UUID tenantId);

    Optional<DeidReleasePolicyEntity> findByTenantIdAndPolicyCode(UUID tenantId, String policyCode);
}
