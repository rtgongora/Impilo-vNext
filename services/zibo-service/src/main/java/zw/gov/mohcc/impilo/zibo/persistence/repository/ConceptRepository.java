package zw.gov.mohcc.impilo.zibo.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ConceptEntity;

/**
 * Reads over the derived concept index.
 *
 * <p>Queries are written against {@code (system, version, code)} and the search vector so they hit
 * the indexes {@code V401} creates. Nothing here scans {@code content_json} — that is the whole
 * point of the projection.</p>
 *
 * <p>Every read spans the national scope and the requesting tenant, and callers prefer a TENANT row
 * over a NATIONAL one for the same code. That mirrors {@code ArtifactResolutionService}: national
 * terminology is visible everywhere, a plane may override it locally.</p>
 */
@Repository
public interface ConceptRepository extends JpaRepository<ConceptEntity, UUID> {

    /** Rebuild-on-publish: a projection is replaced wholesale, never merged. */
    @Modifying
    @Query("DELETE FROM ConceptEntity c WHERE c.artifactId = :artifactId")
    int deleteByArtifactId(@Param("artifactId") UUID artifactId);

    long countByArtifactId(UUID artifactId);

    /**
     * A point lookup for {@code $lookup} / {@code $validate-code}.
     *
     * <p>Ordered so a TENANT override precedes the NATIONAL concept; callers take the first.</p>
     */
    @Query("SELECT c FROM ConceptEntity c WHERE c.system = :system AND c.version = :version "
            + "AND c.code = :code AND (c.tenantId = :tenantId OR c.tenantId IS NULL) "
            + "ORDER BY CASE WHEN c.tenantId IS NULL THEN 1 ELSE 0 END")
    List<ConceptEntity> findForLookup(@Param("system") String system,
                                      @Param("version") String version,
                                      @Param("code") String code,
                                      @Param("tenantId") UUID tenantId);

    /** Every concept of a system/version — the body of an unfiltered {@code $expand}. */
    @Query("SELECT c FROM ConceptEntity c WHERE c.system = :system AND c.version = :version "
            + "AND (c.tenantId = :tenantId OR c.tenantId IS NULL) "
            + "AND (:activeOnly = false OR c.active = true) "
            + "ORDER BY c.code")
    List<ConceptEntity> findForExpansion(@Param("system") String system,
                                         @Param("version") String version,
                                         @Param("tenantId") UUID tenantId,
                                         @Param("activeOnly") boolean activeOnly);

    /** The explicit-concept form of {@code compose.include}: only the codes the ValueSet names. */
    @Query("SELECT c FROM ConceptEntity c WHERE c.system = :system AND c.version = :version "
            + "AND c.code IN :codes AND (c.tenantId = :tenantId OR c.tenantId IS NULL) "
            + "AND (:activeOnly = false OR c.active = true) "
            + "ORDER BY c.code")
    List<ConceptEntity> findForExpansionOfCodes(@Param("system") String system,
                                                @Param("version") String version,
                                                @Param("codes") Collection<String> codes,
                                                @Param("tenantId") UUID tenantId,
                                                @Param("activeOnly") boolean activeOnly);

    /**
     * Free-text search for a type-ahead picker.
     *
     * <p>Native because it uses {@code tsvector} matching and {@code ts_rank}, which JPQL cannot
     * express. Falls back to a prefix match on {@code code} so a clinician typing an ATC or LOINC
     * code — not a word — still finds it; full-text alone would miss {@code N02BE01} entirely.</p>
     */
    @Query(value = """
            SELECT * FROM zibo_concept c
            WHERE c.system = :system AND c.version = :version
              AND (c.tenant_id = :tenantId OR c.tenant_id IS NULL)
              AND (:activeOnly = false OR c.active = true)
              AND (c.search_vector @@ plainto_tsquery('simple', :filter)
                   OR c.code ILIKE :prefix)
            ORDER BY ts_rank(c.search_vector, plainto_tsquery('simple', :filter)) DESC, c.code
            LIMIT :count OFFSET :offset
            """, nativeQuery = true)
    List<ConceptEntity> searchForExpansion(@Param("system") String system,
                                           @Param("version") String version,
                                           @Param("tenantId") UUID tenantId,
                                           @Param("activeOnly") boolean activeOnly,
                                           @Param("filter") String filter,
                                           @Param("prefix") String prefix,
                                           @Param("count") int count,
                                           @Param("offset") int offset);

    @Query("SELECT COUNT(c) FROM ConceptEntity c WHERE c.system = :system AND c.version = :version "
            + "AND (c.tenantId = :tenantId OR c.tenantId IS NULL) "
            + "AND (:activeOnly = false OR c.active = true)")
    long countForExpansion(@Param("system") String system,
                           @Param("version") String version,
                           @Param("tenantId") UUID tenantId,
                           @Param("activeOnly") boolean activeOnly);

    Optional<ConceptEntity> findFirstBySystemAndVersionAndCode(String system, String version, String code);
}
