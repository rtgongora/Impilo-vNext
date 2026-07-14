package zw.gov.mohcc.impilo.referral.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.referral.persistence.entity.SurgicalReferralEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurgicalReferralRepository extends JpaRepository<SurgicalReferralEntity, UUID> {

    Optional<SurgicalReferralEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<SurgicalReferralEntity> findByTenantIdAndReferralId(UUID tenantId, UUID referralId);

    List<SurgicalReferralEntity> findByTenantIdAndDecisionOrderByUpdatedAtDesc(UUID tenantId, String decision);

    List<SurgicalReferralEntity> findByTenantIdAndTargetSpecialtyAndDecisionOrderByUpdatedAtDesc(
            UUID tenantId, String targetSpecialty, String decision);

    List<SurgicalReferralEntity> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
}
