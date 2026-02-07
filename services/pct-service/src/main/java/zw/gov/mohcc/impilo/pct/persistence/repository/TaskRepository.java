package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.TaskEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link TaskEntity} persistence operations.
 * All finder methods are tenant-scoped to enforce multi-tenancy isolation.
 */
@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    /**
     * Finds a specific task by tenant and task ID.
     *
     * @param tenantId the tenant identifier
     * @param taskId   the task identifier
     * @return the task if found
     */
    Optional<TaskEntity> findByTenantIdAndTaskId(UUID tenantId, UUID taskId);

    /**
     * Finds all tasks for a given journey within a tenant.
     *
     * @param tenantId  the tenant identifier
     * @param journeyId the journey identifier
     * @return list of tasks for the journey
     */
    List<TaskEntity> findByTenantIdAndJourneyId(UUID tenantId, String journeyId);

    /**
     * Finds all tasks assigned to a specific provider with a given status within a tenant.
     *
     * @param tenantId   the tenant identifier
     * @param assigneeId the assignee (provider) identifier
     * @param status     the task status to filter by
     * @return list of matching tasks
     */
    List<TaskEntity> findByTenantIdAndAssigneeIdAndStatus(UUID tenantId, String assigneeId, String status);

    /**
     * Finds all tasks at a specific workspace with a given status within a tenant.
     *
     * @param tenantId    the tenant identifier
     * @param workspaceId the workspace identifier
     * @param status      the task status to filter by
     * @return list of matching tasks
     */
    List<TaskEntity> findByTenantIdAndWorkspaceIdAndStatus(UUID tenantId, UUID workspaceId, String status);

    /**
     * Finds all tasks assigned to a specific provider where the status is not the given value.
     * Used to retrieve all non-completed tasks for a provider's task board.
     *
     * @param assigneeId the assignee (provider) identifier
     * @param status     the status to exclude (typically "COMPLETED")
     * @return list of matching tasks
     */
    List<TaskEntity> findByAssigneeIdAndStatusNot(String assigneeId, String status);

    /**
     * Finds all tasks at a specific workspace where the status is not the given value.
     * Used to retrieve all non-completed tasks for a workspace task board.
     *
     * @param workspaceId the workspace identifier
     * @param status      the status to exclude (typically "COMPLETED")
     * @return list of matching tasks
     */
    List<TaskEntity> findByWorkspaceIdAndStatusNot(UUID workspaceId, String status);

    /**
     * Finds all tasks for a given journey (across all tenants).
     * Used when tenant context is already validated by the calling service.
     *
     * @param journeyId the journey identifier
     * @return list of all tasks for the journey
     */
    List<TaskEntity> findByJourneyId(String journeyId);
}
