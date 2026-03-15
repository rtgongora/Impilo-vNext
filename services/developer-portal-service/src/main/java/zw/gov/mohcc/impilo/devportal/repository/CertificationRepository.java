package zw.gov.mohcc.impilo.devportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.devportal.domain.CertificationEntity;

import java.util.List;
import java.util.UUID;

public interface CertificationRepository extends JpaRepository<CertificationEntity, UUID> {
    List<CertificationEntity> findByClientIdOrderByCreatedAtDesc(UUID clientId);
    List<CertificationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
