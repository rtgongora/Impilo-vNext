package zw.gov.mohcc.impilo.butano.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.butano.persistence.entity.ReconciliationMappingEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ReconciliationMappingEntity}.
 *
 * <p>Supports the O-CPID to CPID reconciliation workflow. Queries
 * are scoped by tenant to enforce multi-tenant isolation.</p>
 */
@Repository
public interface ReconciliationMappingRepository extends JpaRepository<ReconciliationMappingEntity, Long> {

    /**
     * Find a reconciliation mapping by tenant and old CPID.
     * The (tenant_id, old_cpid) pair is unique per the database constraint.
     *
     * @param tenantId the tenant's unique identifier
     * @param oldCpid  the provisional CPID being reconciled
     * @return the mapping entity if found
     */
    Optional<ReconciliationMappingEntity> findByTenantIdAndOldCpid(UUID tenantId, String oldCpid);

    /**
     * Find all reconciliation mappings for a tenant filtered by status.
     * Useful for monitoring dashboards and batch processing.
     *
     * @param tenantId the tenant's unique identifier
     * @param status   one of PENDING, IN_PROGRESS, COMPLETED, FAILED
     * @return list of matching mapping entities
     */
    List<ReconciliationMappingEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    /**
     * Find all reconciliation mappings with a given status across all tenants.
     * Used by the scheduled reconciliation processor to pick up pending work.
     *
     * @param status one of PENDING, IN_PROGRESS, COMPLETED, FAILED
     * @return list of matching mapping entities
     */
    List<ReconciliationMappingEntity> findByStatus(String status);
}
