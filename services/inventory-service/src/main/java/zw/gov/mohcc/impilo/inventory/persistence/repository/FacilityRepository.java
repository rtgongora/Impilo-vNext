package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.FacilityEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacilityRepository extends JpaRepository<FacilityEntity, UUID> {

    List<FacilityEntity> findByTenantId(UUID tenantId);

    Optional<FacilityEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);
}
