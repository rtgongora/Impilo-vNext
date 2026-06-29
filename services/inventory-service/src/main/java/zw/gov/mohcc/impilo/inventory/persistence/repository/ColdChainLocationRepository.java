package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ColdChainLocationEntity;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Dura cold-chain locations.
 */
@Repository
public interface ColdChainLocationRepository extends JpaRepository<ColdChainLocationEntity, UUID> {

    List<ColdChainLocationEntity> findByTenantIdOrderByName(UUID tenantId);

    List<ColdChainLocationEntity> findByTenantIdAndFacilityIdOrderByName(UUID tenantId, UUID facilityId);
}
