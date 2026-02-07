package zw.gov.mohcc.impilo.zibo.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.persistence.entity.PackEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link PackEntity} persistence operations.
 * All finder methods are tenant-scoped to enforce multi-tenancy isolation.
 */
@Repository
public interface PackRepository extends JpaRepository<PackEntity, UUID> {

    /**
     * Finds a pack by tenant and pack ID.
     */
    Optional<PackEntity> findByTenantIdAndPackId(UUID tenantId, UUID packId);

    /**
     * Finds a pack by its name and version within a tenant.
     */
    Optional<PackEntity> findByTenantIdAndNameAndVersion(UUID tenantId, String name, String version);

    /**
     * Finds all packs with a given status within a tenant.
     */
    List<PackEntity> findByTenantIdAndStatus(UUID tenantId, ArtifactStatus status);

    /**
     * Returns a paginated list of packs within a tenant.
     */
    Page<PackEntity> findByTenantId(UUID tenantId, Pageable pageable);
}
