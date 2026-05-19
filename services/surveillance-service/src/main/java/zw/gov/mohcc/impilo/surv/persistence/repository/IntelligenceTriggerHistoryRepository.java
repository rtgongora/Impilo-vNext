package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surv.persistence.entity.IntelligenceTriggerHistoryEntity;

public interface IntelligenceTriggerHistoryRepository extends JpaRepository<IntelligenceTriggerHistoryEntity, Long> {
    List<IntelligenceTriggerHistoryEntity> findByTenantIdOrderByIdDesc(UUID tenantId);
    Optional<IntelligenceTriggerHistoryEntity> findFirstByTenantIdAndDedupKeyAndStatus(UUID tenantId, String dedupKey, String status);
}
