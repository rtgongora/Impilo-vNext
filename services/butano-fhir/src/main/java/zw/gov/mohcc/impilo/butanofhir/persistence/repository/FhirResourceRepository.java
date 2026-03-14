package zw.gov.mohcc.impilo.butanofhir.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.butanofhir.persistence.entity.FhirResourceEntity;

@Repository
public interface FhirResourceRepository extends JpaRepository<FhirResourceEntity, Long> {

    List<FhirResourceEntity> findByTenantIdAndResourceType(UUID tenantId, String resourceType);

    Optional<FhirResourceEntity> findByIdAndTenantId(Long id, UUID tenantId);

    Optional<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceId(
            UUID tenantId, String resourceType, String resourceId);
}
