package zw.gov.mohcc.impilo.guidance.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.guidance.persistence.entity.KnowledgeArticleEntity;
import java.util.UUID;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticleEntity, UUID> {

    Page<KnowledgeArticleEntity> findByTenantIdAndStatusOrderByUpdatedAtDesc(
            String tenantId, String status, Pageable pageable);

    Page<KnowledgeArticleEntity> findByTenantIdAndDomainAndStatusOrderByUpdatedAtDesc(
            String tenantId, String domain, String status, Pageable pageable);

    @Query("SELECT a FROM KnowledgeArticleEntity a WHERE a.tenantId = :tenantId AND a.status = 'PUBLISHED' AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(a.summary) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<KnowledgeArticleEntity> search(@Param("tenantId") String tenantId, @Param("q") String query, Pageable pageable);
}
