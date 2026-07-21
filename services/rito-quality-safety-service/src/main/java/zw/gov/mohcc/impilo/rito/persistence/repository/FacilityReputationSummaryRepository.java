package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.FacilityReputationSummaryEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacilityReputationSummaryRepository
        extends JpaRepository<FacilityReputationSummaryEntity, UUID> {

    List<FacilityReputationSummaryEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);

    List<FacilityReputationSummaryEntity> findByTenantIdAndFacilityIdAndReportingPeriod(
            UUID tenantId, UUID facilityId, String reportingPeriod);

    Optional<FacilityReputationSummaryEntity>
        findByTenantIdAndFacilityIdAndDomainAndReportingPeriod(
            UUID tenantId, UUID facilityId, String domain, String reportingPeriod);
}
