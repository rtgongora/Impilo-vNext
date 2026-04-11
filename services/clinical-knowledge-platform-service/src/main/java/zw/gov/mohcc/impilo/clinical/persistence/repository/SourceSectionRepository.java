package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.clinical.persistence.entity.SourceSectionEntity;

import java.util.List;
import java.util.UUID;

public interface SourceSectionRepository extends JpaRepository<SourceSectionEntity, UUID> {

    @Query("""
            select s from SourceSectionEntity s, SourceDocumentEntity d
            where s.documentId = d.id and d.status = 'ACTIVE'
            and (lower(s.rawText) like lower(concat('%', :q, '%'))
                 or lower(coalesce(s.sectionTitle,'')) like lower(concat('%', :q, '%')))
            """)
    List<SourceSectionEntity> searchActive(@Param("q") String q, Pageable pageable);

    /**
     * Removes only PDF-ingested rows ({@code section_path} prefix {@code pdf/}), not curated seed sections.
     */
    @Modifying
    @Transactional
    @Query("delete from SourceSectionEntity s where s.documentId = :documentId and s.sectionPath like 'pdf/%'")
    int deletePdfDerivedSections(@Param("documentId") UUID documentId);

    long countByDocumentId(UUID documentId);

    long countByDocumentIdAndSectionPathStartingWith(UUID documentId, String prefix);
}
