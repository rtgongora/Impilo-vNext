package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccessRequestRepository extends JpaRepository<AccessRequestEntity, UUID> {

    List<AccessRequestEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<AccessRequestEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
}
