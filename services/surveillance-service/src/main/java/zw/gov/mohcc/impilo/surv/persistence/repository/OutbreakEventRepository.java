package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.surv.core.OutbreakLifecycleStatus;
import zw.gov.mohcc.impilo.surv.persistence.entity.OutbreakEventEntity;

@Repository
public interface OutbreakEventRepository extends JpaRepository<OutbreakEventEntity, Long> {

    List<OutbreakEventEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    long countByTenantIdAndLifecycleStatus(UUID tenantId, OutbreakLifecycleStatus status);

    long countByTenantIdAndLifecycleStatusNot(UUID tenantId, OutbreakLifecycleStatus status);
}
