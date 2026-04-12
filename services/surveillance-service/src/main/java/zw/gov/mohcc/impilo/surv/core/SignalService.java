package zw.gov.mohcc.impilo.surv.core;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.SignalEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.SignalRepository;

@Service
public class SignalService {

    private final SignalRepository signalRepository;
    private final EventOutboxRepository outboxRepository;

    public SignalService(SignalRepository signalRepository,
                         EventOutboxRepository outboxRepository) {
        this.signalRepository = signalRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public SignalEntity createSignal(UUID tenantId, String actorId, UUID correlationId,
                                     String name, String description,
                                     String eventType, String conditionField,
                                     int threshold, int windowHours,
                                     Severity severity) {
        SignalEntity signal = new SignalEntity();
        signal.setTenantId(tenantId);
        signal.setName(name);
        signal.setDescription(description);
        signal.setEventType(eventType);
        signal.setConditionField(conditionField);
        signal.setThreshold(threshold > 0 ? threshold : 1);
        signal.setWindowHours(windowHours > 0 ? windowHours : 24);
        signal.setSeverity(severity != null ? severity : Severity.MEDIUM);
        signal.setCreatedBy(actorId);
        signal = signalRepository.save(signal);

        publishEvent("SIGNAL", signal.getId().toString(), "SIGNAL_CREATED",
                buildSignalPayload(signal), tenantId.toString(), correlationId,
                correlationId != null ? correlationId.toString() : null);

        return signal;
    }

    @Transactional(readOnly = true)
    public List<SignalEntity> listSignals(UUID tenantId) {
        return signalRepository.findByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public List<SignalEntity> findActiveByEventType(UUID tenantId, String eventType) {
        return signalRepository.findByTenantIdAndEventTypeAndStatus(tenantId, eventType, SignalStatus.ACTIVE);
    }

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

    private String buildSignalPayload(SignalEntity s) {
        return "{\"id\":" + s.getId()
                + ",\"tenantId\":\"" + s.getTenantId() + "\""
                + ",\"name\":\"" + s.getName() + "\""
                + ",\"eventType\":\"" + s.getEventType() + "\""
                + ",\"conditionField\":\"" + s.getConditionField() + "\""
                + ",\"threshold\":" + s.getThreshold()
                + ",\"windowHours\":" + s.getWindowHours()
                + ",\"severity\":\"" + s.getSeverity() + "\""
                + "}";
    }
}
