package zw.gov.mohcc.impilo.tshepo.keys.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.CertificateTrustEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateTrustRepository extends JpaRepository<CertificateTrustEntity, Long> {

    Optional<CertificateTrustEntity> findByFingerprintSha256(String fingerprintSha256);

    List<CertificateTrustEntity> findByTenantIdAndStatusOrderByImportedAtDesc(UUID tenantId, String status);

    List<CertificateTrustEntity> findByTenantIdOrderByImportedAtDesc(UUID tenantId);

    Optional<CertificateTrustEntity> findByIdAndTenantId(Long id, UUID tenantId);

    boolean existsByFingerprintSha256(String fingerprintSha256);
}
