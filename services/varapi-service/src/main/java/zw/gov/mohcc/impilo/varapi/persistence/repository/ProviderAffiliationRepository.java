package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAffiliationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderAffiliationRepository extends JpaRepository<ProviderAffiliationEntity, Long> {

    List<ProviderAffiliationEntity> findByProviderId(Long providerId);

    Optional<ProviderAffiliationEntity> findByIdAndTenantId(Long id, UUID tenantId);

    List<ProviderAffiliationEntity> findByTenantIdAndProviderId(UUID tenantId, Long providerId);

    List<ProviderAffiliationEntity> findByFacilityId(Long facilityId);

    List<ProviderAffiliationEntity> findByTenantIdAndFacilityId(UUID tenantId, Long facilityId);

    List<ProviderAffiliationEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    List<ProviderAffiliationEntity> findByProviderIdAndStatus(Long providerId, String status);

    List<ProviderAffiliationEntity> findByTenantIdAndProviderIdAndStatus(UUID tenantId, Long providerId, String status);

    List<ProviderAffiliationEntity> findByProviderIdAndPrimaryFlag(Long providerId, Boolean primaryFlag);

    List<ProviderAffiliationEntity> findByTenantIdAndProviderIdAndPrimaryFlag(UUID tenantId, Long providerId, Boolean primaryFlag);

    int countByFacilityIdAndStatus(Long facilityId, String status);

    List<ProviderAffiliationEntity> findByFacilityIdAndStatus(Long facilityId, String status);

    List<ProviderAffiliationEntity> findByTenantIdAndFacilityIdAndStatus(UUID tenantId, Long facilityId, String status);

    List<ProviderAffiliationEntity> findByStatusIn(List<String> statuses);

    List<ProviderAffiliationEntity> findByTenantIdAndStatusIn(UUID tenantId, List<String> statuses);

    List<ProviderAffiliationEntity> findByTenantIdAndApprovalState(UUID tenantId, String approvalState);
}
