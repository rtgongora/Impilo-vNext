package zw.gov.mohcc.impilo.landela.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.landela.persistence.entity.DocumentAuditEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentAuditRepository extends JpaRepository<DocumentAuditEntity, Long> {

    List<DocumentAuditEntity> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
