package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodComponentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodComponentRepository extends JpaRepository<BloodComponentEntity, Long> {
    Optional<BloodComponentEntity> findByComponentIdAndTenantId(UUID componentId, UUID tenantId);
    List<BloodComponentEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
