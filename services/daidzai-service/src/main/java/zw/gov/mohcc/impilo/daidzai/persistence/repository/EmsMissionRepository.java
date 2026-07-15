package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsMissionEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmsMissionRepository extends JpaRepository<EmsMissionEntity, UUID> {
    Optional<EmsMissionEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    /** Idempotency anchor: one clinical mission per incident. */
    Optional<EmsMissionEntity> findByTenantIdAndIncidentId(UUID tenantId, UUID incidentId);
}
