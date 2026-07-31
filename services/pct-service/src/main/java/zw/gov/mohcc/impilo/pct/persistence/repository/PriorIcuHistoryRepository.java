package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.PriorIcuHistoryEntity;

import java.util.List;
import java.util.UUID;

public interface PriorIcuHistoryRepository extends JpaRepository<PriorIcuHistoryEntity, UUID> {
    List<PriorIcuHistoryEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
