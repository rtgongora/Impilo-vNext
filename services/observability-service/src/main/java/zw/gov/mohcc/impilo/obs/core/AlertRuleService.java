package zw.gov.mohcc.impilo.obs.core;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.obs.persistence.entity.AlertRuleEntity;
import zw.gov.mohcc.impilo.obs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.obs.persistence.repository.AlertRuleRepository;
import zw.gov.mohcc.impilo.obs.persistence.repository.EventOutboxRepository;

@Service
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final EventOutboxRepository outboxRepository;

    public AlertRuleService(AlertRuleRepository alertRuleRepository,
                            EventOutboxRepository outboxRepository) {
        this.alertRuleRepository = alertRuleRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public AlertRuleEntity createAlertRule(UUID tenantId, String actorId, UUID correlationId,
                                           String name, String description,
                                           String metricName, AlertCondition condition,
                                           BigDecimal threshold, AlertSeverity severity) {
        AlertRuleEntity entity = new AlertRuleEntity();
        entity.setTenantId(tenantId);
        entity.setName(name);
        entity.setDescription(description);
        entity.setMetricName(metricName);
        entity.setCondition(condition != null ? condition : AlertCondition.GREATER_THAN);
        entity.setThreshold(threshold != null ? threshold : BigDecimal.ZERO);
        entity.setSeverity(severity != null ? severity : AlertSeverity.WARNING);
        entity.setCreatedBy(actorId);
        entity = alertRuleRepository.save(entity);

        publishEvent("ALERT_RULE", entity.getId().toString(), "ALERT_RULE_CREATED",
                buildAlertRulePayload(entity), tenantId.toString(), correlationId);

        return entity;
    }

    @Transactional(readOnly = true)
    public List<AlertRuleEntity> listAlertRules(UUID tenantId) {
        return alertRuleRepository.findByTenantId(tenantId);
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

    private String buildAlertRulePayload(AlertRuleEntity a) {
        return "{\"id\":" + a.getId()
                + ",\"tenantId\":\"" + a.getTenantId() + "\""
                + ",\"name\":\"" + a.getName() + "\""
                + ",\"metricName\":\"" + a.getMetricName() + "\""
                + ",\"condition\":\"" + a.getCondition() + "\""
                + ",\"threshold\":" + a.getThreshold()
                + ",\"severity\":\"" + a.getSeverity() + "\""
                + "}";
    }
}
