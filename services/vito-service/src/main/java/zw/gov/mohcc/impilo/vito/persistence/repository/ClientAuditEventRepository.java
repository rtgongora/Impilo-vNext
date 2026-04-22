package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientAuditEventEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientAuditEventRepository extends JpaRepository<ClientAuditEventEntity, UUID> {
    List<ClientAuditEventEntity> findByClientHealthIdOrderByCreatedAtDesc(UUID clientHealthId);
    List<ClientAuditEventEntity> findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(UUID tenantId, UUID clientHealthId);
}
