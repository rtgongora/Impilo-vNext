package zw.gov.mohcc.impilo.dags.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.dags.persistence.entity.PolicyEntity;

@Repository
public interface PolicyRepository extends JpaRepository<PolicyEntity, Long> {

    List<PolicyEntity> findByTenantId(UUID tenantId);

    List<PolicyEntity> findByTenantIdAndResourceType(UUID tenantId, String resourceType);
}
