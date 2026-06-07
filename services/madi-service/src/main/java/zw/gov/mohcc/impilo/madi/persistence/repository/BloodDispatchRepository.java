package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodDispatchEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodDispatchRepository extends JpaRepository<BloodDispatchEntity, Long> {
    Optional<BloodDispatchEntity> findByDispatchIdAndTenantId(UUID dispatchId, UUID tenantId);
    List<BloodDispatchEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
