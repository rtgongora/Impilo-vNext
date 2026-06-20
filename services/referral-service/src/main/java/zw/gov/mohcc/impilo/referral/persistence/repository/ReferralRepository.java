package zw.gov.mohcc.impilo.referral.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.referral.persistence.entity.ReferralEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<ReferralEntity, UUID> {

    List<ReferralEntity> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, String patientId);

    Optional<ReferralEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
