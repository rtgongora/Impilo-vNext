package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralPackageEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralPackageRepository extends JpaRepository<ReferralPackageEntity, UUID> {
    Optional<ReferralPackageEntity> findByTenantIdAndReferralId(UUID tenantId, UUID referralId);

    List<ReferralPackageEntity> findByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);

    List<ReferralPackageEntity> findByTenantIdAndEncounterIdOrderByCreatedAtDesc(UUID tenantId, Long encounterId);

    List<ReferralPackageEntity> findByTenantIdAndFacilityIdOrderByCreatedAtDesc(UUID tenantId, UUID facilityId);

    List<ReferralPackageEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
