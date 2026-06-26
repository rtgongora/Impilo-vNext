package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.SlaPolicyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SlaPolicyRepository extends JpaRepository<SlaPolicyEntity, UUID> {

    Optional<SlaPolicyEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<SlaPolicyEntity> findByTenantIdAndPolicyKey(UUID tenantId, String policyKey);

    List<SlaPolicyEntity> findByTenantIdAndActiveTrue(UUID tenantId);
}
