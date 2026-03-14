package zw.gov.mohcc.impilo.offlineedge.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.offlineedge.domain.ReconciliationBatchEntity;
import java.util.UUID;

public interface ReconciliationBatchRepository extends JpaRepository<ReconciliationBatchEntity, UUID> {
}
