package zw.gov.mohcc.impilo.support.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.support.domain.KnowledgeArticleEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ArticleRepository extends JpaRepository<KnowledgeArticleEntity, UUID> {
    @Query("SELECT a FROM KnowledgeArticleEntity a WHERE a.tenantId = :tenantId"
            + " AND (:category IS NULL OR a.category = :category)"
            + " AND (:status IS NULL OR a.status = :status)")
    Page<KnowledgeArticleEntity> findFiltered(@Param("tenantId") UUID tenantId,
                                               @Param("category") String category,
                                               @Param("status") String status,
                                               Pageable pageable);

    @Query("SELECT a FROM KnowledgeArticleEntity a WHERE a.createdAt <= :asOf")
    Page<KnowledgeArticleEntity> findSnapshotAsOf(@Param("asOf") OffsetDateTime asOf, Pageable pageable);
}
