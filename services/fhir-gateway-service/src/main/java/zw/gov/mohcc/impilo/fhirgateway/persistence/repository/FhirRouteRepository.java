package zw.gov.mohcc.impilo.fhirgateway.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirRouteEntity;

@Repository
public interface FhirRouteRepository extends JpaRepository<FhirRouteEntity, Long> {

    List<FhirRouteEntity> findByTenantIdAndEnabledTrue(UUID tenantId);

    List<FhirRouteEntity> findByTenantId(UUID tenantId);

    List<FhirRouteEntity> findByTenantIdAndResourceTypeAndEnabledTrue(UUID tenantId, String resourceType);
}
