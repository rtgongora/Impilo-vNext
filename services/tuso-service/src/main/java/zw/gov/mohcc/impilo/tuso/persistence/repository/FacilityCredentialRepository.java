package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCredentialEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacilityCredentialRepository extends JpaRepository<FacilityCredentialEntity, Long> {

    Optional<FacilityCredentialEntity> findByVerificationCode(String verificationCode);

    List<FacilityCredentialEntity> findByTenantIdAndFacilityUuidOrderByIssuedAtDesc(
            UUID tenantId, UUID facilityUuid);
}
