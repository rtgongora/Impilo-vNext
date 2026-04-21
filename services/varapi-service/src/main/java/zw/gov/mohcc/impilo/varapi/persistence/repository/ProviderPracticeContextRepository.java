package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderPracticeContextEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderPracticeContextRepository extends JpaRepository<ProviderPracticeContextEntity, Long> {

    List<ProviderPracticeContextEntity> findByProviderId(Long providerId);

    List<ProviderPracticeContextEntity> findByTenantIdAndContextType(UUID tenantId, String contextType);

    List<ProviderPracticeContextEntity> findByProviderIdAndStatus(Long providerId, String status);

    List<ProviderPracticeContextEntity> findByProviderIdAndContextType(Long providerId, String contextType);

    List<ProviderPracticeContextEntity> findByLinkedFacilityIdAndStatus(Long facilityId, String status);
}