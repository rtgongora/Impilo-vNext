package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<ReferralEntity, UUID> {
    Optional<ReferralEntity> findByTenantIdAndReferralId(UUID tenantId, UUID referralId);
    List<ReferralEntity> findByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);
    List<ReferralEntity> findByTenantIdAndFacilityIdOrderByCreatedAtDesc(UUID tenantId, String facilityId);
    List<ReferralEntity> findByTenantIdAndFacilityIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String facilityId, String status);
}
