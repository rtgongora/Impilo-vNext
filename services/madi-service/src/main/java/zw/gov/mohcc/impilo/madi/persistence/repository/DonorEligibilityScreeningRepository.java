package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonorEligibilityScreeningEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonorEligibilityScreeningRepository extends JpaRepository<DonorEligibilityScreeningEntity, Long> {
    Optional<DonorEligibilityScreeningEntity> findByScreeningIdAndTenantId(UUID screeningId, UUID tenantId);
    List<DonorEligibilityScreeningEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
