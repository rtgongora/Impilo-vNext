package zw.gov.mohcc.impilo.surv.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surv.persistence.entity.AlertEventEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertEventRepository extends JpaRepository<AlertEventEntity, Long> {
    List<AlertEventEntity> findByTenantId(UUID tenantId);
    List<AlertEventEntity> findByTenantIdAndTriggeredAtAfter(UUID tenantId, OffsetDateTime since);
    List<AlertEventEntity> findByTenantIdAndAcknowledgedFalse(UUID tenantId);
    Optional<AlertEventEntity> findByIdAndTenantId(Long id, UUID tenantId);

    long countByTenantIdAndAcknowledgedFalse(UUID tenantId);
}
