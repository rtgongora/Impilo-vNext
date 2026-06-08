package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonorPreScreeningEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonorPreScreeningRepository extends JpaRepository<DonorPreScreeningEntity, Long> {
    Optional<DonorPreScreeningEntity> findFirstByTenantIdAndDonorIdOrderByCreatedAtDesc(UUID tenantId, UUID donorId);
}
