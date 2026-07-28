package zw.gov.mohcc.impilo.mentalhealth.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.AdmissionRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionRequestRepository extends JpaRepository<AdmissionRequestEntity, UUID> {
    Optional<AdmissionRequestEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<AdmissionRequestEntity> findByTenantIdAndReferralIdOrderByRequestedAtDesc(UUID tenantId, UUID referralId);
}
