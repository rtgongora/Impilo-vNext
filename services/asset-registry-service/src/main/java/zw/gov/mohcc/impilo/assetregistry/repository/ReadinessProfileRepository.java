package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.ReadinessProfileEntity;

import java.util.List;
import java.util.UUID;

public interface ReadinessProfileRepository extends JpaRepository<ReadinessProfileEntity, UUID> {
    List<ReadinessProfileEntity> findByTenantIdAndFacilityRef(UUID tenantId, String facilityRef);
}
