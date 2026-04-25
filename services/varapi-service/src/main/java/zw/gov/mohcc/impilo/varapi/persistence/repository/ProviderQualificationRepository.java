package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderQualificationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderQualificationRepository extends JpaRepository<ProviderQualificationEntity, Long> {

    List<ProviderQualificationEntity> findByProviderId(Long providerId);

    Optional<ProviderQualificationEntity> findByIdAndTenantId(Long id, UUID tenantId);

    List<ProviderQualificationEntity> findByTenantIdAndProviderId(UUID tenantId, Long providerId);

    List<ProviderQualificationEntity> findByTenantIdAndVerificationStatus(UUID tenantId, String verificationStatus);

    List<ProviderQualificationEntity> findByProviderIdAndVerificationStatus(Long providerId, String verificationStatus);

    List<ProviderQualificationEntity> findByTenantIdAndProviderIdAndVerificationStatus(
            UUID tenantId,
            Long providerId,
            String verificationStatus);

    List<ProviderQualificationEntity> findByVerificationStatusIn(List<String> verificationStatuses);

    List<ProviderQualificationEntity> findByTenantIdAndVerificationStatusIn(UUID tenantId, List<String> verificationStatuses);

    @Query("select q from ProviderQualificationEntity q where q.institutionName = :awardingBody")
    List<ProviderQualificationEntity> findByAwardingBody(@Param("awardingBody") String awardingBody);

    @Query("select q from ProviderQualificationEntity q where q.tenantId = :tenantId and q.institutionName = :awardingBody")
    List<ProviderQualificationEntity> findByTenantIdAndAwardingBody(@Param("tenantId") UUID tenantId, @Param("awardingBody") String awardingBody);

    List<ProviderQualificationEntity> findByQualificationLevel(String qualificationLevel);

    List<ProviderQualificationEntity> findByTenantIdAndQualificationLevel(UUID tenantId, String qualificationLevel);
}
