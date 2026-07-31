package zw.gov.mohcc.impilo.tshepo.identity.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.LostDeviceRecoveryCaseEntity;

public interface LostDeviceRecoveryCaseRepository extends JpaRepository<LostDeviceRecoveryCaseEntity, UUID> {
    Optional<LostDeviceRecoveryCaseEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
