package zw.gov.mohcc.impilo.secharden.core;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.secharden.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.secharden.persistence.entity.ScanResultEntity;
import zw.gov.mohcc.impilo.secharden.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.secharden.persistence.repository.ScanResultRepository;

@Service
public class ScanService {

    private final ScanResultRepository scanResultRepository;
    private final EventOutboxRepository outboxRepository;

    public ScanService(ScanResultRepository scanResultRepository,
                       EventOutboxRepository outboxRepository) {
        this.scanResultRepository = scanResultRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public ScanResultEntity runScan(UUID tenantId, String actorId, UUID correlationId,
                                    Long packId, String target,
                                    int passed, int failed, int skipped,
                                    String details) {
        ScanResultEntity entity = new ScanResultEntity();
        entity.setTenantId(tenantId);
        entity.setPackId(packId);
        entity.setTarget(target);
        entity.setPassed(passed);
        entity.setFailed(failed);
        entity.setSkipped(skipped);
        entity.setDetails(details != null ? details : "[]");
        entity.setScannedBy(actorId);
        entity = scanResultRepository.save(entity);

        publishEvent("SCAN_RESULT", entity.getId().toString(), "SCAN_COMPLETED",
                buildScanPayload(entity), tenantId.toString(), correlationId);

        return entity;
    }

    @Transactional(readOnly = true)
    public List<ScanResultEntity> listScans(UUID tenantId) {
        return scanResultRepository.findByTenantId(tenantId);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, String payload,
                              String tenantId, UUID correlationId) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setTenantId(tenantId);
        event.setCorrelationId(correlationId != null ? correlationId.toString() : null);
        outboxRepository.save(event);
    }

    private String buildScanPayload(ScanResultEntity s) {
        return "{\"id\":" + s.getId()
                + ",\"tenantId\":\"" + s.getTenantId() + "\""
                + ",\"packId\":" + s.getPackId()
                + ",\"target\":\"" + s.getTarget() + "\""
                + ",\"passed\":" + s.getPassed()
                + ",\"failed\":" + s.getFailed()
                + ",\"skipped\":" + s.getSkipped()
                + "}";
    }
}
