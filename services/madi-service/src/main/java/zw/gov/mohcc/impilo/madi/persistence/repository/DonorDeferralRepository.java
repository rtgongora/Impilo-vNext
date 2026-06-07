package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonorDeferralEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonorDeferralRepository extends JpaRepository<DonorDeferralEntity, Long> {
    Optional<DonorDeferralEntity> findByDeferralIdAndTenantId(UUID deferralId, UUID tenantId);
    List<DonorDeferralEntity> findByDonorIdAndTenantIdAndStatus(UUID donorId, UUID tenantId, String status);
    List<DonorDeferralEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
