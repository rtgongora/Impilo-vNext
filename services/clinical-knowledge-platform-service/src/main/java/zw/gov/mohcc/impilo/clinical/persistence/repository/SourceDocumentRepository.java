package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.clinical.persistence.entity.SourceDocumentEntity;

import java.util.Optional;
import java.util.UUID;

public interface SourceDocumentRepository extends JpaRepository<SourceDocumentEntity, UUID> {

    Optional<SourceDocumentEntity> findFirstByStatusOrderByEffectiveDateDesc(String status);
}
