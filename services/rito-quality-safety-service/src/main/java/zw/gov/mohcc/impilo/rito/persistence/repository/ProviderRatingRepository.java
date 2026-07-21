package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderRatingEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderRatingRepository extends JpaRepository<ProviderRatingEntity, UUID> {

    List<ProviderRatingEntity> findByTenantIdAndProviderPublicId(UUID tenantId, String providerPublicId);

    /** All ratings at a facility (across every provider) — drives facility reputation (RW8). */
    List<ProviderRatingEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);

    /** Distinct (tenant, facility, period) with PUBLISHED ratings — facility recompute discovery. */
    @Query("SELECT DISTINCT r.tenantId, r.facilityId, r.reportingPeriod "
            + "FROM ProviderRatingEntity r WHERE r.moderationState = 'PUBLISHED' AND r.facilityId IS NOT NULL")
    List<Object[]> findDistinctPublishedFacilityPeriods();

    /** Distinct (tenant, provider, period) tuples with PUBLISHED ratings — drives scheduled recompute. */
    @Query("SELECT DISTINCT r.tenantId, r.providerPublicId, r.reportingPeriod "
            + "FROM ProviderRatingEntity r WHERE r.moderationState = 'PUBLISHED'")
    List<Object[]> findDistinctPublishedProviderPeriods();

    List<ProviderRatingEntity> findByTenantIdAndEncounterRef(UUID tenantId, String encounterRef);

    List<ProviderRatingEntity> findByTenantIdAndModerationState(UUID tenantId, String moderationState);

    /** Anti-manipulation invariant: at most one verified rating per (encounter, provider). */
    boolean existsByTenantIdAndEncounterRefAndProviderPublicIdAndVerifiedInteractionTrue(
            UUID tenantId, String encounterRef, String providerPublicId);
}
