package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportExceptionRepository extends JpaRepository<ImportExceptionEntity, UUID> {
    List<ImportExceptionEntity> findByImportBatchIdOrderByCreatedAtAsc(UUID importBatchId);
}
