package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.ProviderAccreditationEntity;

public interface ProviderAccreditationRepository extends JpaRepository<ProviderAccreditationEntity, UUID> {

    List<ProviderAccreditationEntity> findByTenantIdAndProviderIdOrderByAccreditedAtDesc(UUID tenantId, UUID providerId);
}
