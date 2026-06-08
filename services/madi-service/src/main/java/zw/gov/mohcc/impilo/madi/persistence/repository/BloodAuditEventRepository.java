package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodAuditEventEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodAuditEventRepository extends JpaRepository<BloodAuditEventEntity, Long> {
    Optional<BloodAuditEventEntity> findByAuditEventIdAndTenantId(UUID auditEventId, UUID tenantId);
    List<BloodAuditEventEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
