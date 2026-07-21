package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderReputationSummaryEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderReputationSummaryRepository
        extends JpaRepository<ProviderReputationSummaryEntity, UUID> {

    List<ProviderReputationSummaryEntity> findByTenantIdAndProviderPublicId(
            UUID tenantId, String providerPublicId);

    /** Verified-experience public read: the "all facilities" domain rollups for a provider/period. */
    List<ProviderReputationSummaryEntity>
        findByTenantIdAndProviderPublicIdAndFacilityIdIsNullAndServicePointIdIsNullAndReportingPeriod(
            UUID tenantId, String providerPublicId, String reportingPeriod);
}
