package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCertificateEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderCertificateRepository extends JpaRepository<ProviderCertificateEntity, Long> {

    List<ProviderCertificateEntity> findByProviderId(Long providerId);

    Optional<ProviderCertificateEntity> findByIdAndTenantId(Long id, UUID tenantId);

    List<ProviderCertificateEntity> findByTenantIdAndProviderId(UUID tenantId, Long providerId);

    List<ProviderCertificateEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    List<ProviderCertificateEntity> findByProviderIdAndStatus(Long providerId, String status);

    List<ProviderCertificateEntity> findByTenantIdAndProviderIdAndStatus(UUID tenantId, Long providerId, String status);

    List<ProviderCertificateEntity> findByTenantIdAndProviderIdAndStatusOrderByExpiryDateDescIssueDateDescIdDesc(
            UUID tenantId,
            Long providerId,
            String status);

    Optional<ProviderCertificateEntity> findByProviderIdAndCertificateTypeAndStatus(Long providerId, String certificateType, String status);

    Optional<ProviderCertificateEntity> findByTenantIdAndProviderIdAndCertificateTypeAndStatus(
            UUID tenantId,
            Long providerId,
            String certificateType,
            String status);

    List<ProviderCertificateEntity> findByExpiryDateBefore(LocalDate date);

    List<ProviderCertificateEntity> findByTenantIdAndExpiryDateBefore(UUID tenantId, LocalDate date);

    Optional<ProviderCertificateEntity> findByCertificateNumber(String certificateNumber);

    Optional<ProviderCertificateEntity> findByCertificateNumberAndTenantId(String certificateNumber, UUID tenantId);

    List<ProviderCertificateEntity> findByStatusAndExpiryDateBefore(String status, LocalDate date);

    List<ProviderCertificateEntity> findByTenantIdAndStatusAndExpiryDateBefore(UUID tenantId, String status, LocalDate date);
}
