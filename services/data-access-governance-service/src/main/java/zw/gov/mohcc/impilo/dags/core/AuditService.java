package zw.gov.mohcc.impilo.dags.core;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.dags.persistence.entity.AuditLogEntity;
import zw.gov.mohcc.impilo.dags.persistence.repository.AuditLogRepository;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(UUID tenantId, String action, String actorId,
                    String resourceType, String resourceId,
                    String details, UUID correlationId) {
        AuditLogEntity entry = new AuditLogEntity();
        entry.setTenantId(tenantId);
        entry.setAction(action);
        entry.setActorId(actorId);
        entry.setResourceType(resourceType);
        entry.setResourceId(resourceId);
        entry.setDetails(details);
        entry.setCorrelationId(correlationId);
        auditLogRepository.save(entry);
    }
}
