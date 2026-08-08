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

import java.util.Collection;
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

    long countByTenantId(UUID tenantId);

    /**
     * Every artifact of a type in any of the given statuses, across all tenant planes.
     *
     * <p>Deliberately not tenant-scoped: this backs concept reprojection, which must rebuild the
     * index for the whole estate. Seeded terminology is split across the registry and care planes
     * — a tenant-scoped rebuild would silently leave one plane's vocabularies unsearchable, which
     * is the failure mode the national-plane fallback exists to prevent.</p>
     */
    List<ArtifactEntity> findByFhirTypeAndStatusIn(ArtifactType fhirType,
                                                   Collection<ArtifactStatus> statuses);

    /**
     * Finds all versions of an artifact identified by canonical URL.
     *
     * <p>Ordered by {@code versionSortKey} descending, <b>not</b> by creation date. Creation order
     * and version order agree only by coincidence: a 1.0.1 correction published after 1.1.0 is
     * newer by clock and older by version, and for external terminologies whose versions are
     * release dates the two have no relationship at all. Nulls sort last so a row predating the
     * {@code V400} backfill can never outrank a real version.</p>
     */
    @Query("SELECT a FROM ArtifactEntity a WHERE a.tenantId = :tenantId AND a.canonicalUrl = :canonicalUrl "
            + "ORDER BY a.versionSortKey DESC NULLS LAST, a.createdAt DESC")
    List<ArtifactEntity> findAllVersions(@Param("tenantId") UUID tenantId, @Param("canonicalUrl") String canonicalUrl);

    /**
     * Candidate artifacts for a canonical URL across the requesting tenant and the national plane.
     *
     * <p>Both planes are queried in one pass so the caller can prefer a tenant-local artifact and
     * fall back to the national one without a second round trip. Every ZIBO lookup was previously
     * {@code findByTenantId…}, which is why {@code V007} inserts the ATC CodeSystem twice — once
     * per plane — to work around terminology being invisible across the boundary.</p>
     */
    @Query("SELECT a FROM ArtifactEntity a WHERE a.canonicalUrl = :canonicalUrl "
            + "AND a.tenantId IN :tenantIds AND a.fhirType = :fhirType "
            + "ORDER BY a.versionSortKey DESC NULLS LAST, a.createdAt DESC")
    List<ArtifactEntity> findCandidatesAcrossPlanes(@Param("canonicalUrl") String canonicalUrl,
                                                    @Param("tenantIds") Collection<UUID> tenantIds,
                                                    @Param("fhirType") ArtifactType fhirType);
}
