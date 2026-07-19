package zw.gov.mohcc.impilo.abis.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.abis.core.BiometricModality;
import zw.gov.mohcc.impilo.abis.persistence.entity.TemplateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemplateRepository extends JpaRepository<TemplateEntity, Long> {

    Optional<TemplateEntity> findByTenantIdAndSubjectRefAndModalityAndPosition(
            UUID tenantId, String subjectRef, BiometricModality modality, String position);

    Optional<TemplateEntity> findFirstByTenantIdAndSubjectRefAndModalityAndStatusOrderByUpdatedAtDesc(
            UUID tenantId, String subjectRef, BiometricModality modality, String status);

    List<TemplateEntity> findByTenantIdAndModalityAndStatus(
            UUID tenantId, BiometricModality modality, String status, Pageable pageable);
}
