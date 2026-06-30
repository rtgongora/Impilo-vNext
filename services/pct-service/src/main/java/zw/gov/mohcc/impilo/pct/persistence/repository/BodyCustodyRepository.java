package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.BodyCustodyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BodyCustodyRepository extends JpaRepository<BodyCustodyEntity, UUID> {
    Optional<BodyCustodyEntity> findByTenantIdAndCaseId(UUID tenantId, UUID caseId);
    List<BodyCustodyEntity> findByTenantIdAndFacilityIdAndStatus(UUID tenantId, UUID facilityId, String status);
    List<BodyCustodyEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);
}
