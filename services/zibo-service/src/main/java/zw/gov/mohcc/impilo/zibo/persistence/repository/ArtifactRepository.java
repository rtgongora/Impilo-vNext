package zw.gov.mohcc.impilo.zibo.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ArtifactEntity} persistence operations.
 * All finder methods are tenant-scoped to enforce multi-tenancy isolation.
 */
@Repository
public interface ArtifactRepository extends JpaRepository<ArtifactEntity, UUID> {

    /**
     * Finds an artifact by tenant and artifact ID.
     */
    Optional<ArtifactEntity> findByTenantIdAndArtifactId(UUID tenantId, UUID artifactId);

    /**
     * Finds an artifact by its canonical URL and version within a tenant.
     */
    Optional<ArtifactEntity> findByTenantIdAndCanonicalUrlAndVersion(UUID tenantId, String canonicalUrl, String version);

    /**
     * Finds all artifacts of a given FHIR type within a tenant.
     */
    List<ArtifactEntity> findByTenantIdAndFhirType(UUID tenantId, ArtifactType fhirType);

    /**
     * Finds all artifacts with a given status within a tenant.
     */
    List<ArtifactEntity> findByTenantIdAndStatus(UUID tenantId, ArtifactStatus status);

    /**
     * Finds all artifacts matching a canonical URL within a tenant (all versions).
     */
    List<ArtifactEntity> findByTenantIdAndCanonicalUrl(UUID tenantId, String canonicalUrl);

    /**
     * Returns a paginated list of artifacts within a tenant.
     */
    Page<ArtifactEntity> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * Checks whether an artifact with the given canonical URL and version exists within a tenant.
     */
    boolean existsByTenantIdAndCanonicalUrlAndVersion(UUID tenantId, String canonicalUrl, String version);

    /**
     * Finds all versions of an artifact identified by canonical URL, ordered by creation date descending.
     */
    @Query("SELECT a FROM ArtifactEntity a WHERE a.tenantId = :tenantId AND a.canonicalUrl = :canonicalUrl ORDER BY a.createdAt DESC")
    List<ArtifactEntity> findAllVersions(@Param("tenantId") UUID tenantId, @Param("canonicalUrl") String canonicalUrl);
}
