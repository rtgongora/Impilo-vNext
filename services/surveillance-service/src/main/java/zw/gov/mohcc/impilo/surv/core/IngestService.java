package zw.gov.mohcc.impilo.surv.core;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.SignalEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.SignalHitEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.CaseRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.SignalHitRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.SignalRepository;

@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final SignalRepository signalRepository;
    private final SignalHitRepository signalHitRepository;
    private final CaseRepository caseRepository;
    private final EventOutboxRepository outboxRepository;

    public IngestService(SignalRepository signalRepository,
                         SignalHitRepository signalHitRepository,
                         CaseRepository caseRepository,
                         EventOutboxRepository outboxRepository) {
        this.signalRepository = signalRepository;
        this.signalHitRepository = signalHitRepository;
        this.caseRepository = caseRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public IngestResult ingest(UUID tenantId, String actorId, UUID correlationId,
                               String eventType, String payload, UUID facilityId,
                               String idempotencyKey) {
        List<SignalEntity> activeSignals =
                signalRepository.findByTenantIdAndEventTypeAndStatus(tenantId, eventType, SignalStatus.ACTIVE);

        int hitsRecorded = 0;
        int casesOpened = 0;

        for (SignalEntity signal : activeSignals) {
            SignalHitEntity hit = new SignalHitEntity();
            hit.setTenantId(tenantId);
            hit.setSignalId(signal.getId());
            hit.setEventType(eventType);
            hit.setEventPayload(payload != null ? payload : "{}");
            hit.setHitCount(1);
            hit.setFacilityId(facilityId);
            hit = signalHitRepository.save(hit);
            hitsRecorded++;

            publishEvent("SIGNAL_HIT", hit.getId().toString(), "SIGNAL_HIT",
                    buildHitPayload(hit, signal.getName()),
                    tenantId.toString(), correlationId, idempotencyKey);

            if (signal.getThreshold() <= 1) {
                CaseEntity caseEntity = new CaseEntity();
                caseEntity.setTenantId(tenantId);
                caseEntity.setSignalHitId(hit.getId());
                caseEntity.setCaseType(signal.getEventType());
                caseEntity.setTitle("Auto-case: " + signal.getName());
                caseEntity.setDescription("Automatically opened from signal hit #" + hit.getId());
                caseEntity.setSeverity(signal.getSeverity());
                caseEntity.setFacilityId(facilityId);
                caseEntity.setOpenedBy(actorId);
                caseEntity = caseRepository.save(caseEntity);
                casesOpened++;

                publishEvent("CASE", caseEntity.getId().toString(), "CASE_OPENED",
                        buildCasePayload(caseEntity),
                        tenantId.toString(), correlationId, idempotencyKey);

                log.info("Case #{} auto-opened from signal '{}' hit #{}",
                        caseEntity.getId(), signal.getName(), hit.getId());
            }
        }

        return new IngestResult(activeSignals.size(), hitsRecorded, casesOpened);
    }

    public record IngestResult(int signalsMatched, int hitsRecorded, int casesOpened) {}

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, String payload,
                              String tenantId, UUID correlationId, String idempotencyKey) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setTenantId(tenantId);
        event.setCorrelationId(correlationId != null ? correlationId.toString() : null);
        event.setIdempotencyKey(idempotencyKey);
        event.setOccurredAt(OffsetDateTime.now());
        outboxRepository.save(event);
    }

    private String buildHitPayload(SignalHitEntity hit, String signalName) {
        return "{\"id\":" + hit.getId()
                + ",\"signalId\":" + hit.getSignalId()
                + ",\"signalName\":\"" + signalName + "\""
                + ",\"eventType\":\"" + hit.getEventType() + "\""
                + ",\"hitCount\":" + hit.getHitCount()
                + "}";
    }

    private String buildCasePayload(CaseEntity c) {
        return "{\"id\":" + c.getId()
                + ",\"tenantId\":\"" + c.getTenantId() + "\""
                + ",\"caseType\":\"" + c.getCaseType() + "\""
                + ",\"title\":\"" + c.getTitle() + "\""
                + ",\"severity\":\"" + c.getSeverity() + "\""
                + ",\"status\":\"" + c.getStatus() + "\""
                + "}";
    }
}
