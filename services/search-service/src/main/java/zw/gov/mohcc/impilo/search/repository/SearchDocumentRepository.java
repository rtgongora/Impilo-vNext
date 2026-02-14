package zw.gov.mohcc.impilo.search.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.search.domain.SearchDocumentEntity;

import java.util.Optional;

@Repository
public interface SearchDocumentRepository extends JpaRepository<SearchDocumentEntity, String> {

    Optional<SearchDocumentEntity> findByTenantIdAndIndexIdAndExternalId(
            String tenantId, String indexId, String externalId);

    @Query("SELECT d FROM SearchDocumentEntity d WHERE d.tenantId = :tenantId " +
            "AND (:indexId IS NULL OR d.indexId = :indexId) " +
            "AND (LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "     OR LOWER(d.bodyText) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<SearchDocumentEntity> search(
            @Param("tenantId") String tenantId,
            @Param("indexId") String indexId,
            @Param("query") String query,
            Pageable pageable);
}
