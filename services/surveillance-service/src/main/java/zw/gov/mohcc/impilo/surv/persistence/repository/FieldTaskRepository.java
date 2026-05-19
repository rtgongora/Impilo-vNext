package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.surv.core.FieldTaskStatus;
import zw.gov.mohcc.impilo.surv.persistence.entity.FieldTaskEntity;

@Repository
public interface FieldTaskRepository extends JpaRepository<FieldTaskEntity, Long> {

    List<FieldTaskEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, FieldTaskStatus status);

    long countByTenantIdAndStatusIn(UUID tenantId, List<FieldTaskStatus> statuses);
}
