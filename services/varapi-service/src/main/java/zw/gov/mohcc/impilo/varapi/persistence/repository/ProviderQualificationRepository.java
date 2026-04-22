package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderQualificationEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderQualificationRepository extends JpaRepository<ProviderQualificationEntity, Long> {

    List<ProviderQualificationEntity> findByProviderId(Long providerId);

    List<ProviderQualificationEntity> findByTenantIdAndVerificationStatus(UUID tenantId, String verificationStatus);

    List<ProviderQualificationEntity> findByProviderIdAndVerificationStatus(Long providerId, String verificationStatus);

    List<ProviderQualificationEntity> findByVerificationStatusIn(List<String> verificationStatuses);

    List<ProviderQualificationEntity> findByAwardingBody(String awardingBody);

    List<ProviderQualificationEntity> findByQualificationLevel(String qualificationLevel);
}