package zw.gov.mohcc.impilo.surv.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.AlertDefinitionEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.AlertEventEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.DailyCounterEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.AlertDefinitionRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.AlertEventRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.DailyCounterRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.EventOutboxRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CounterService {

    private static final Logger log = LoggerFactory.getLogger(CounterService.class);

    private final DailyCounterRepository counterRepository;
    private final AlertDefinitionRepository alertDefRepository;
    private final AlertEventRepository alertEventRepository;
    private final EventOutboxRepository outboxRepository;

    public CounterService(DailyCounterRepository counterRepository,
                           AlertDefinitionRepository alertDefRepository,
                           AlertEventRepository alertEventRepository,
                           EventOutboxRepository outboxRepository) {
        this.counterRepository = counterRepository;
        this.alertDefRepository = alertDefRepository;
        this.alertEventRepository = alertEventRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public DailyCounterEntity incrementCounter(UUID tenantId, UUID facilityId, String syndromeCode, LocalDate date) {
        Optional<DailyCounterEntity> existing = counterRepository
                .findByTenantIdAndFacilityIdAndSyndromeCodeAndCountDate(tenantId, facilityId, syndromeCode, date);

        DailyCounterEntity counter;
        if (existing.isPresent()) {
            counter = existing.get();
            counter.setEventCount(counter.getEventCount() + 1);
            counter.setLastUpdatedAt(OffsetDateTime.now());
        } else {
            counter = new DailyCounterEntity();
            counter.setTenantId(tenantId);
            counter.setFacilityId(facilityId);
            counter.setSyndromeCode(syndromeCode);
            counter.setCountDate(date);
            counter.setEventCount(1);
        }

        counterRepository.save(counter);

        // Check alert thresholds
        checkAlertThresholds(tenantId, facilityId, syndromeCode, counter.getEventCount());

        return counter;
    }

    private void checkAlertThresholds(UUID tenantId, UUID facilityId, String syndromeCode, int currentCount) {
        List<AlertDefinitionEntity> alertDefs = alertDefRepository
                .findByTenantIdAndSyndromeCodeAndActiveTrue(tenantId, syndromeCode);

        for (AlertDefinitionEntity alertDef : alertDefs) {
            if (currentCount >= alertDef.getThreshold()) {
                AlertEventEntity alertEvent = new AlertEventEntity();
                alertEvent.setTenantId(tenantId);
                alertEvent.setAlertDefinitionId(alertDef.getId());
                alertEvent.setFacilityId(facilityId);
                alertEvent.setSyndromeCode(syndromeCode);
                alertEvent.setTriggerCount(currentCount);
                alertEvent.setThreshold(alertDef.getThreshold());
                alertEvent = alertEventRepository.save(alertEvent);

                publishAlertOutbox(alertEvent, alertDef.getName());

                log.info("Alert triggered [syndrome={}, facility={}, count={}, threshold={}]",
                        syndromeCode, facilityId, currentCount, alertDef.getThreshold());
            }
        }
    }

    public List<DailyCounterEntity> getCounters(UUID tenantId, LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return counterRepository.findByTenantIdAndCountDateBetween(tenantId, from, to);
        }
        return counterRepository.findByTenantId(tenantId);
    }

    public List<AlertEventEntity> getAlerts(UUID tenantId, boolean unacknowledgedOnly) {
        if (unacknowledgedOnly) {
            return alertEventRepository.findByTenantIdAndAcknowledgedFalse(tenantId);
        }
        return alertEventRepository.findByTenantId(tenantId);
    }

    public List<AlertDefinitionEntity> getAlertDefinitions(UUID tenantId) {
        return alertDefRepository.findByTenantIdAndActiveTrue(tenantId);
    }

    private void publishAlertOutbox(AlertEventEntity alert, String definitionName) {
        EventOutboxEntity row = new EventOutboxEntity();
        row.setAggregateType("ALERT");
        row.setAggregateId(String.valueOf(alert.getId()));
        row.setEventType("ALERT_TRIGGERED");
        row.setTenantId(alert.getTenantId().toString());
        row.setOccurredAt(OffsetDateTime.now());
        row.setPayload("{\"alertEventId\":" + alert.getId()
                + ",\"tenantId\":\"" + alert.getTenantId() + "\""
                + ",\"facilityId\":\"" + alert.getFacilityId() + "\""
                + ",\"syndromeCode\":\"" + alert.getSyndromeCode() + "\""
                + ",\"definitionName\":\"" + (definitionName != null ? definitionName.replace("\"", "'") : "")
                + "\",\"triggerCount\":" + alert.getTriggerCount()
                + ",\"threshold\":" + alert.getThreshold()
                + "}");
        outboxRepository.save(row);
    }

    @Transactional
    public AlertDefinitionEntity createAlertDefinition(UUID tenantId, String name, String syndromeCode,
                                                        int threshold, int windowDays, String severity) {
        AlertDefinitionEntity def = new AlertDefinitionEntity();
        def.setTenantId(tenantId);
        def.setName(name);
        def.setSyndromeCode(syndromeCode);
        def.setThreshold(threshold);
        def.setWindowDays(windowDays);
        def.setSeverity(severity != null ? severity : "MEDIUM");
        def.setActive(true);
        return alertDefRepository.save(def);
    }
}
