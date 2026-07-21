package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderRatingEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderRatingRepository extends JpaRepository<ProviderRatingEntity, UUID> {

    List<ProviderRatingEntity> findByTenantIdAndProviderPublicId(UUID tenantId, String providerPublicId);

    List<ProviderRatingEntity> findByTenantIdAndEncounterRef(UUID tenantId, String encounterRef);

    List<ProviderRatingEntity> findByTenantIdAndModerationState(UUID tenantId, String moderationState);

    /** Anti-manipulation invariant: at most one verified rating per (encounter, provider). */
    boolean existsByTenantIdAndEncounterRefAndProviderPublicIdAndVerifiedInteractionTrue(
            UUID tenantId, String encounterRef, String providerPublicId);
}
