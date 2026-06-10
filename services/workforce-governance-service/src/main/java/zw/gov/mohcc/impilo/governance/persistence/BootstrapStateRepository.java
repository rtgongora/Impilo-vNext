package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BootstrapStateRepository extends JpaRepository<BootstrapStateEntity, UUID> {
    Optional<BootstrapStateEntity> findByTenantId(UUID tenantId);
}
