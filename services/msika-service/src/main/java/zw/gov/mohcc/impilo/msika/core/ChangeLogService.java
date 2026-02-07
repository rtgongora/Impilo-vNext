package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msika.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msika.persistence.entity.ChangeLogEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.ChangeLogRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.UUID;

@Service
public class ChangeLogService {

    private static final Logger log = LoggerFactory.getLogger(ChangeLogService.class);
    private final ChangeLogRepository changeLogRepository;
    private final ObjectMapper objectMapper;

    public ChangeLogService(ChangeLogRepository changeLogRepository, ObjectMapper objectMapper) {
        this.changeLogRepository = changeLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(String action, String entityType, String entityId, Object diff) {
        TrustContext ctx = TrustContextHolder.require();
        ChangeLogEntity entry = new ChangeLogEntity();
        entry.setId(UlidGenerator.generate());
        entry.setTenantId(ctx.tenantId());
        entry.setActorId(ctx.actorId() != null ? ctx.actorId() : "SYSTEM");
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        try {
            entry.setDiff(objectMapper.writeValueAsString(diff != null ? diff : "{}"));
        } catch (Exception e) {
            entry.setDiff("{}");
            log.warn("Failed to serialize changelog diff for {} {}: {}", entityType, entityId, e.getMessage());
        }
        changeLogRepository.save(entry);
    }

    public void record(UUID tenantId, String actorId, String action, String entityType, String entityId, Object diff) {
        ChangeLogEntity entry = new ChangeLogEntity();
        entry.setId(UlidGenerator.generate());
        entry.setTenantId(tenantId);
        entry.setActorId(actorId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        try {
            entry.setDiff(objectMapper.writeValueAsString(diff != null ? diff : "{}"));
        } catch (Exception e) {
            entry.setDiff("{}");
        }
        changeLogRepository.save(entry);
    }
}
