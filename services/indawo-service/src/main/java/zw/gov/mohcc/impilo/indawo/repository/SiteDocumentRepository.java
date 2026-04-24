package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.indawo.domain.SiteDocumentEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SiteDocumentRepository extends JpaRepository<SiteDocumentEntity, UUID> {
    List<SiteDocumentEntity> findBySiteIdOrderByUploadedAtDesc(UUID siteId);
    List<SiteDocumentEntity> findByApplicationIdOrderByUploadedAtDesc(UUID applicationId);
}

