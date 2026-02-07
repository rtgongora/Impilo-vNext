package zw.gov.mohcc.impilo.zibo.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.zibo.domain.ScopeType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.AssignmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link AssignmentEntity} persistence operations.
 * Manages the binding of terminology packs to scopes (tenant, facility, workspace).
 */
@Repository
public interface AssignmentRepository extends JpaRepository<AssignmentEntity, UUID> {

    /**
     * Finds all assignments for a specific scope within a tenant.
     */
    List<AssignmentEntity> findByTenantIdAndScopeTypeAndScopeId(UUID tenantId, ScopeType scopeType, UUID scopeId);

    /**
     * Finds all assignments referencing a specific pack within a tenant.
     */
    List<AssignmentEntity> findByTenantIdAndPackId(UUID tenantId, UUID packId);

    /**
     * Finds a specific assignment of a pack to a scope within a tenant.
     */
    Optional<AssignmentEntity> findByTenantIdAndScopeTypeAndScopeIdAndPackId(UUID tenantId, ScopeType scopeType, UUID scopeId, UUID packId);

    /**
     * Finds all active assignments for a specific scope within a tenant.
     */
    List<AssignmentEntity> findByTenantIdAndScopeTypeAndScopeIdAndActiveTrue(UUID tenantId, ScopeType scopeType, UUID scopeId);
}
