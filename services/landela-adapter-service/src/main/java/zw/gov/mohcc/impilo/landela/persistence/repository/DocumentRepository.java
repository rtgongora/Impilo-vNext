package zw.gov.mohcc.impilo.landela.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.landela.persistence.entity.DocumentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    Optional<DocumentEntity> findByDocumentId(UUID documentId);

    Page<DocumentEntity> findByTenantIdAndSubjectTypeAndSubjectId(
            UUID tenantId, String subjectType, String subjectId, Pageable pageable);

    Page<DocumentEntity> findByTenantIdAndLifecycleState(
            UUID tenantId, String lifecycleState, Pageable pageable);

    List<DocumentEntity> findByHashSha256(String hashSha256);
}
