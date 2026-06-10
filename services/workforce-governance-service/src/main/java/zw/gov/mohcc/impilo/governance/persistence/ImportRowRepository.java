package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportRowRepository extends JpaRepository<ImportRowEntity, UUID> {
    List<ImportRowEntity> findByImportBatchIdOrderByRowNumberAsc(UUID importBatchId);
}
