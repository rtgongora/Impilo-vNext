package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.oros.persistence.entity.PrescriptionClaimEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrescriptionClaimRepository extends JpaRepository<PrescriptionClaimEntity, UUID> {

    Optional<PrescriptionClaimEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    List<PrescriptionClaimEntity> findByPrescriptionIdOrderByClaimedAtDesc(String prescriptionId);

    Optional<PrescriptionClaimEntity> findByTenantIdAndClaimId(UUID tenantId, UUID claimId);
}
