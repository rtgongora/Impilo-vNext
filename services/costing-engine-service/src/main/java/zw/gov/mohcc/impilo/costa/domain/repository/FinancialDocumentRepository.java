package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.FinancialDocumentEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialDocumentRepository extends JpaRepository<FinancialDocumentEntity, Long> {

    Optional<FinancialDocumentEntity> findByTenantIdAndDocId(UUID tenantId, UUID docId);

    @Query("""
            SELECT d FROM FinancialDocumentEntity d
            WHERE d.tenantId = :tenantId AND d.subjectRef = :subjectRef
            AND (:subjectType IS NULL OR d.subjectType = :subjectType)
            AND (:docType IS NULL OR d.docType = :docType)
            ORDER BY d.generatedAt DESC
            """)
    Page<FinancialDocumentEntity> search(
            @Param("tenantId") UUID tenantId,
            @Param("subjectRef") String subjectRef,
            @Param("subjectType") String subjectType,
            @Param("docType") String docType,
            Pageable pageable);
}
