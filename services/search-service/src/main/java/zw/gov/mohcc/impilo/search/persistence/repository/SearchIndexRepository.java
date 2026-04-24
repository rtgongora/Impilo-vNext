package zw.gov.mohcc.impilo.search.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.search.persistence.entity.SearchIndexEntity;

import java.util.Optional;

@Repository
public interface SearchIndexRepository extends JpaRepository<SearchIndexEntity, String> {

    Page<SearchIndexEntity> findByTenantIdAndEntityType(String tenantId, String entityType, Pageable pageable);

    Page<SearchIndexEntity> findByTenantId(String tenantId, Pageable pageable);

    @Query("SELECT s FROM SearchIndexEntity s WHERE s.tenantId = :tenantId " +
            "AND LOWER(s.searchableText) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<SearchIndexEntity> searchByText(@Param("tenantId") String tenantId,
                                         @Param("query") String query,
                                         Pageable pageable);

    @Query("SELECT s.id FROM SearchIndexEntity s WHERE s.tenantId = :tenantId " +
            "AND LOWER(s.searchableText) LIKE LOWER(CONCAT('%', :query, '%'))")
    Slice<String> searchIdsByText(@Param("tenantId") String tenantId,
                                   @Param("query") String query,
                                   Pageable pageable);

    @Query("SELECT s FROM SearchIndexEntity s WHERE s.tenantId = :tenantId " +
            "AND LOWER(s.searchableText) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "AND s.entityType = :entityType")
    Page<SearchIndexEntity> searchByTextAndEntityType(@Param("tenantId") String tenantId,
                                                      @Param("query") String query,
                                                      @Param("entityType") String entityType,
                                                      Pageable pageable);

    @Query("SELECT s.id FROM SearchIndexEntity s WHERE s.tenantId = :tenantId " +
            "AND LOWER(s.searchableText) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "AND s.entityType = :entityType")
    Slice<String> searchIdsByTextAndEntityType(@Param("tenantId") String tenantId,
                                               @Param("query") String query,
                                               @Param("entityType") String entityType,
                                               Pageable pageable);

    @Query("SELECT s FROM SearchIndexEntity s WHERE s.tenantId = :tenantId " +
            "AND LOWER(s.tags) LIKE LOWER(CONCAT('%', :tag, '%'))")
    Page<SearchIndexEntity> findByTenantIdAndTagsContaining(@Param("tenantId") String tenantId,
                                                             @Param("tag") String tag,
                                                             Pageable pageable);

    Optional<SearchIndexEntity> findByEntityTypeAndEntityIdAndTenantId(String entityType,
                                                                       String entityId,
                                                                       String tenantId);
}
