package zw.gov.mohcc.impilo.secharden.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.secharden.persistence.entity.PolicyPackEntity;

@Repository
public interface PolicyPackRepository extends JpaRepository<PolicyPackEntity, Long> {

    List<PolicyPackEntity> findByTenantId(UUID tenantId);
}
