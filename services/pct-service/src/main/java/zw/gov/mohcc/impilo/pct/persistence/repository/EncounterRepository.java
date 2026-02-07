package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link EncounterEntity} persistence operations.
 * All finder methods are tenant-scoped to enforce multi-tenancy isolation.
 */
@Repository
public interface EncounterRepository extends JpaRepository<EncounterEntity, Long> {

    /**
     * Finds all encounters for a given journey within a tenant.
     *
     * @param tenantId  the tenant identifier
     * @param journeyId the journey identifier
     * @return list of encounters in the journey
     */
    List<EncounterEntity> findByTenantIdAndJourneyId(UUID tenantId, String journeyId);

    /**
     * Finds a specific encounter by tenant and encounter ID.
     *
     * @param tenantId the tenant identifier
     * @param id       the encounter ID
     * @return the encounter if found
     */
    Optional<EncounterEntity> findByTenantIdAndId(UUID tenantId, Long id);

    /**
     * Finds all encounters with a given status within a tenant.
     *
     * @param tenantId the tenant identifier
     * @param status   the encounter status to filter by
     * @return list of matching encounters
     */
    List<EncounterEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    /**
     * Finds encounters for a specific workspace and status within a tenant.
     * Useful for finding active encounters at a workspace.
     *
     * @param tenantId    the tenant identifier
     * @param workspaceId the workspace identifier
     * @param status      the encounter status to filter by
     * @return list of matching encounters
     */
    List<EncounterEntity> findByTenantIdAndWorkspaceIdAndStatus(UUID tenantId, UUID workspaceId, String status);

    /**
     * Finds all encounters for a given journey (across tenants).
     * Used when tenant context is already validated by the calling service.
     *
     * @param journeyId the journey identifier
     * @return list of encounters in the journey
     */
    List<EncounterEntity> findByJourneyId(String journeyId);
}
