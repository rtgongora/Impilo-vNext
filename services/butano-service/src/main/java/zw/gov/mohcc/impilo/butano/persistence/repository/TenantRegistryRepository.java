package zw.gov.mohcc.impilo.butano.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.butano.persistence.entity.TenantRegistryEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link TenantRegistryEntity}.
 *
 * <p>Provides lookup operations for tenant management in the BUTANO
 * shared health record. Used by the tenant enforcement interceptor to
 * validate incoming requests and by admin endpoints for tenant CRUD.</p>
 */
@Repository
public interface TenantRegistryRepository extends JpaRepository<TenantRegistryEntity, Long> {

    /**
     * Find a tenant by its UUID.
     *
     * @param tenantId the tenant's unique identifier
     * @return the tenant entity if found
     */
    Optional<TenantRegistryEntity> findByTenantId(UUID tenantId);

    /**
     * Find all active tenants.
     *
     * @return list of active tenant entities
     */
    List<TenantRegistryEntity> findAllByActiveTrue();

    /**
     * Check whether a tenant with the given UUID exists.
     *
     * @param tenantId the tenant's unique identifier
     * @return true if a tenant entity exists with this ID
     */
    boolean existsByTenantId(UUID tenantId);
}
