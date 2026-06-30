package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.ResourceRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResourceRequestRepository extends JpaRepository<ResourceRequestEntity, UUID> {
    List<ResourceRequestEntity> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
    Optional<ResourceRequestEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
