package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.MciCasualtyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MciCasualtyRepository extends JpaRepository<MciCasualtyEntity, UUID> {
    List<MciCasualtyEntity> findByTenantIdAndIncidentIdOrderByTaggedAtAsc(UUID tenantId, UUID incidentId);
    Optional<MciCasualtyEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    long countByTenantIdAndIncidentId(UUID tenantId, UUID incidentId);

    /** The bulk-mint pass's own query: casualties not yet minted onto a pct episode. */
    @Query("select c from MciCasualtyEntity c where c.tenantId = :tenantId and c.incidentId = :incidentId "
            + "and c.emergencyEpisodeId is null order by c.taggedAt asc")
    List<MciCasualtyEntity> findUnminted(@Param("tenantId") UUID tenantId, @Param("incidentId") UUID incidentId);
}
