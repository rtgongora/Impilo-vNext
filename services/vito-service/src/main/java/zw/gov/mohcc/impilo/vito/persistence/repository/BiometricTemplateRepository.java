package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.core.BiometricModality;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricTemplateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BiometricTemplateRepository extends JpaRepository<BiometricTemplateEntity, Long> {

    List<BiometricTemplateEntity> findByTenantIdAndHealthIdAndStatus(
            UUID tenantId, UUID healthId, String status);

    Optional<BiometricTemplateEntity> findByTenantIdAndHealthIdAndModalityAndPositionAndStatus(
            UUID tenantId, UUID healthId, BiometricModality modality, String position, String status);

    List<BiometricTemplateEntity> findByTenantIdAndModalityAndStatus(
            UUID tenantId, BiometricModality modality, String status);
}
