package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderPracticeContextEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderPracticeContextRepository extends JpaRepository<ProviderPracticeContextEntity, Long> {

    List<ProviderPracticeContextEntity> findByProviderId(Long providerId);

    Optional<ProviderPracticeContextEntity> findByIdAndTenantId(Long id, UUID tenantId);

    List<ProviderPracticeContextEntity> findByTenantIdAndProviderId(UUID tenantId, Long providerId);

    List<ProviderPracticeContextEntity> findByTenantIdAndContextType(UUID tenantId, String contextType);

    List<ProviderPracticeContextEntity> findByProviderIdAndStatus(Long providerId, String status);

    List<ProviderPracticeContextEntity> findByTenantIdAndProviderIdAndStatus(UUID tenantId, Long providerId, String status);

    List<ProviderPracticeContextEntity> findByProviderIdAndContextType(Long providerId, String contextType);

    List<ProviderPracticeContextEntity> findByTenantIdAndProviderIdAndContextType(UUID tenantId, Long providerId, String contextType);

    List<ProviderPracticeContextEntity> findByLinkedFacilityIdAndStatus(Long facilityId, String status);

    List<ProviderPracticeContextEntity> findByTenantIdAndLinkedFacilityIdAndStatus(UUID tenantId, Long facilityId, String status);

    List<ProviderPracticeContextEntity> findByStatus(String status);

    List<ProviderPracticeContextEntity> findByTenantIdAndStatus(UUID tenantId, String status);
}
