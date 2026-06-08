package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodEmergencyRedistributionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BloodEmergencyRedistributionRepository
        extends JpaRepository<BloodEmergencyRedistributionEntity, Long> {

    Optional<BloodEmergencyRedistributionEntity> findByRedistributionIdAndTenantId(UUID redistributionId, UUID tenantId);

    List<BloodEmergencyRedistributionEntity> findByTenantIdOrderByRequestedAtDesc(UUID tenantId);
}
