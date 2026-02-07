package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.WorkspaceSessionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link WorkspaceSessionEntity} persistence operations.
 * All finder methods are tenant-scoped to enforce multi-tenancy isolation.
 */
@Repository
public interface WorkspaceSessionRepository extends JpaRepository<WorkspaceSessionEntity, UUID> {

    /**
     * Finds the active (not yet ended) session for a given actor within a tenant.
     * An actor should have at most one active session at a time.
     *
     * @param tenantId the tenant identifier
     * @param actorId  the actor (provider) identifier
     * @return the active session if one exists
     */
    Optional<WorkspaceSessionEntity> findByTenantIdAndActorIdAndEndedAtIsNull(UUID tenantId, String actorId);

    /**
     * Finds active sessions at a specific workspace within a facility and tenant.
     * Useful for determining how many providers are currently staffing a workspace.
     *
     * @param tenantId    the tenant identifier
     * @param facilityId  the facility identifier
     * @param workspaceId the workspace identifier
     * @return list of active sessions at the workspace
     */
    List<WorkspaceSessionEntity> findByTenantIdAndFacilityIdAndWorkspaceIdAndEndedAtIsNull(
            UUID tenantId, UUID facilityId, UUID workspaceId);
}
