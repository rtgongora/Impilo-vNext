package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAffiliationEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderAffiliationRepository extends JpaRepository<ProviderAffiliationEntity, Long> {

    List<ProviderAffiliationEntity> findByProviderId(Long providerId);

    List<ProviderAffiliationEntity> findByFacilityId(Long facilityId);

    List<ProviderAffiliationEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    List<ProviderAffiliationEntity> findByProviderIdAndStatus(Long providerId, String status);

    List<ProviderAffiliationEntity> findByProviderIdAndPrimaryFlag(Long providerId, Boolean primaryFlag);

    int countByFacilityIdAndStatus(Long facilityId, String status);

    List<ProviderAffiliationEntity> findByFacilityIdAndStatus(Long facilityId, String status);

    List<ProviderAffiliationEntity> findByStatusIn(List<String> statuses);
}