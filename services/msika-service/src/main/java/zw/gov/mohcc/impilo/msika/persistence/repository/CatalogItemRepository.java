package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogItemEntity;

import java.util.List;
import java.util.Optional;

public interface CatalogItemRepository extends JpaRepository<CatalogItemEntity, String> {
    Page<CatalogItemEntity> findByCatalogId(String catalogId, Pageable pageable);
    Page<CatalogItemEntity> findByCatalogIdAndKind(String catalogId, String kind, Pageable pageable);
    List<CatalogItemEntity> findByCatalogIdAndKind(String catalogId, String kind);
    Optional<CatalogItemEntity> findByCatalogIdAndCanonicalCode(String catalogId, String canonicalCode);
    long countByCatalogId(String catalogId);

    /**
     * FTS search over items in PUBLISHED catalogs. Tenant handling is honest:
     * a NULL :tenantId means "platform catalogue only" (tenant_id IS NULL rows),
     * a concrete tenant sees its own catalogs plus the platform ones.
     */
    @Query(value = "SELECT * FROM msika_catalog_items WHERE search_vector @@ plainto_tsquery('english', :query)"
            + " AND (:kind IS NULL OR kind = :kind)"
            + " AND (:restriction IS NULL OR jsonb_exists(restrictions, :restriction))"
            + " AND (:tag IS NULL OR :tag = ANY(tags))"
            + " AND (:ziboCode IS NULL OR zibo_bindings @> jsonb_build_array(jsonb_build_object('code', :ziboCode)))"
            + " AND catalog_id IN (SELECT catalog_id FROM msika_catalogs WHERE status = 'PUBLISHED'"
            + " AND (tenant_id IS NULL OR tenant_id = CAST(:tenantId AS uuid)))",
           countQuery = "SELECT count(*) FROM msika_catalog_items WHERE search_vector @@ plainto_tsquery('english', :query)"
            + " AND (:kind IS NULL OR kind = :kind)"
            + " AND (:restriction IS NULL OR jsonb_exists(restrictions, :restriction))"
            + " AND (:tag IS NULL OR :tag = ANY(tags))"
            + " AND (:ziboCode IS NULL OR zibo_bindings @> jsonb_build_array(jsonb_build_object('code', :ziboCode)))"
            + " AND catalog_id IN (SELECT catalog_id FROM msika_catalogs WHERE status = 'PUBLISHED'"
            + " AND (tenant_id IS NULL OR tenant_id = CAST(:tenantId AS uuid)))",
           nativeQuery = true)
    Page<CatalogItemEntity> searchItems(@Param("query") String query, @Param("kind") String kind,
                                        @Param("restriction") String restriction, @Param("tag") String tag,
                                        @Param("ziboCode") String ziboCode,
                                        @Param("tenantId") String tenantId, Pageable pageable);

    /** Browse (no query) over items in PUBLISHED catalogs; same honest tenant semantics as searchItems. */
    @Query(value = "SELECT * FROM msika_catalog_items WHERE catalog_id IN"
            + " (SELECT catalog_id FROM msika_catalogs WHERE status = 'PUBLISHED'"
            + " AND (tenant_id IS NULL OR tenant_id = CAST(:tenantId AS uuid)))"
            + " AND (:kind IS NULL OR kind = :kind)"
            + " AND (:restriction IS NULL OR jsonb_exists(restrictions, :restriction))"
            + " AND (:tag IS NULL OR :tag = ANY(tags))"
            + " AND (:ziboCode IS NULL OR zibo_bindings @> jsonb_build_array(jsonb_build_object('code', :ziboCode)))",
           countQuery = "SELECT count(*) FROM msika_catalog_items WHERE catalog_id IN"
            + " (SELECT catalog_id FROM msika_catalogs WHERE status = 'PUBLISHED'"
            + " AND (tenant_id IS NULL OR tenant_id = CAST(:tenantId AS uuid)))"
            + " AND (:kind IS NULL OR kind = :kind)"
            + " AND (:restriction IS NULL OR jsonb_exists(restrictions, :restriction))"
            + " AND (:tag IS NULL OR :tag = ANY(tags))"
            + " AND (:ziboCode IS NULL OR zibo_bindings @> jsonb_build_array(jsonb_build_object('code', :ziboCode)))",
           nativeQuery = true)
    Page<CatalogItemEntity> findPublishedItems(@Param("kind") String kind,
                                               @Param("restriction") String restriction, @Param("tag") String tag,
                                               @Param("ziboCode") String ziboCode,
                                               @Param("tenantId") String tenantId, Pageable pageable);

    @Query(value = "SELECT * FROM msika_catalog_items WHERE catalog_id IN (SELECT catalog_id FROM msika_catalogs WHERE status = 'PUBLISHED' AND (tenant_id = CAST(:tenantId AS uuid) OR tenant_id IS NULL)) AND :tag = ANY(tags)",
           nativeQuery = true)
    List<CatalogItemEntity> findByTag(@Param("tag") String tag, @Param("tenantId") String tenantId);

    @Query(value = "SELECT * FROM msika_catalog_items WHERE catalog_id IN (SELECT catalog_id FROM msika_catalogs WHERE status = 'PUBLISHED' AND (tenant_id = CAST(:tenantId AS uuid) OR tenant_id IS NULL)) AND zibo_bindings @> CAST(:binding AS jsonb)",
           nativeQuery = true)
    List<CatalogItemEntity> findByZiboBinding(@Param("binding") String binding, @Param("tenantId") String tenantId);
}
