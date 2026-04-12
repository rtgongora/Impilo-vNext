package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.CostCenterEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CostCenterRepository extends JpaRepository<CostCenterEntity, Long> {

    List<CostCenterEntity> findByTenantIdAndFacilityIdOrderByCodeAsc(UUID tenantId, UUID facilityId);

    Optional<CostCenterEntity> findByTenantIdAndFacilityIdAndCenterId(UUID tenantId, UUID facilityId, UUID centerId);

    Optional<CostCenterEntity> findByTenantIdAndFacilityIdAndCode(UUID tenantId, UUID facilityId, String code);
}
