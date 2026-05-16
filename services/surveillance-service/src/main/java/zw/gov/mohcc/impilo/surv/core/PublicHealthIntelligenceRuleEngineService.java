package zw.gov.mohcc.impilo.surv.core;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.*;
import zw.gov.mohcc.impilo.surv.persistence.repository.*;

@Service
public class PublicHealthIntelligenceRuleEngineService {
    private final PhAlertRuleRepository ruleRepository;
    private final IntelligenceRuleConditionRepository conditionRepository;
    private final IntelligenceRuleEvaluationRepository evaluationRepository;
    private final IntelligenceTriggerHistoryRepository triggerHistoryRepository;
    private final IntelligenceAlertRepository alertRepository;
    private final IntelligenceGeneratedTaskRepository generatedTaskRepository;
    private final InspectionScheduleRepository inspectionScheduleRepository;
    private final PublicHealthOperationsHomeService operationsHomeService;

    public PublicHealthIntelligenceRuleEngineService(
            PhAlertRuleRepository ruleRepository,
            IntelligenceRuleConditionRepository conditionRepository,
            IntelligenceRuleEvaluationRepository evaluationRepository,
            IntelligenceTriggerHistoryRepository triggerHistoryRepository,
            IntelligenceAlertRepository alertRepository,
            IntelligenceGeneratedTaskRepository generatedTaskRepository,
            InspectionScheduleRepository inspectionScheduleRepository,
            PublicHealthOperationsHomeService operationsHomeService) {
        this.ruleRepository = ruleRepository;
        this.conditionRepository = conditionRepository;
        this.evaluationRepository = evaluationRepository;
        this.triggerHistoryRepository = triggerHistoryRepository;
        this.alertRepository = alertRepository;
        this.generatedTaskRepository = generatedTaskRepository;
        this.inspectionScheduleRepository = inspectionScheduleRepository;
        this.operationsHomeService = operationsHomeService;
    }

    @Transactional
    public IntelligenceRuleConditionEntity createRuleCondition(
            UUID tenantId, Long ruleId, String metricKey, String comparisonOperator, double thresholdValue, String scopeRef) {
        IntelligenceRuleConditionEntity entity = new IntelligenceRuleConditionEntity();
        entity.setTenantId(tenantId);
        entity.setRuleId(ruleId);
        entity.setMetricKey(normalize(metricKey, "open_alerts"));
        entity.setComparisonOperator(normalize(comparisonOperator, "GT"));
        entity.setThresholdValue(thresholdValue);
        entity.setScopeRef(scopeRef);
        return conditionRepository.save(entity);
    }

    @Transactional
    public Map<String, Object> evaluateRule(UUID tenantId, Long ruleId, String mode) {
        PhAlertRuleEntity rule = ruleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));
        List<IntelligenceRuleConditionEntity> conditions = conditionRepository.findByTenantIdAndRuleIdAndActiveTrue(tenantId, ruleId);
        Map<String, Object> kpis = operationsHomeService.buildHome(tenantId);
        Map<String, Object> kpiMap = (Map<String, Object>) kpis.getOrDefault("kpis", Map.of());
        boolean triggered = false;
        String message = "No trigger";
        for (IntelligenceRuleConditionEntity condition : conditions) {
            double observed = asDouble(kpiMap.get(condition.getMetricKey()));
            if (matches(observed, condition.getComparisonOperator(), condition.getThresholdValue())) {
                triggered = true;
                message = condition.getMetricKey() + " " + condition.getComparisonOperator() + " " + condition.getThresholdValue() + " (observed " + observed + ")";
                break;
            }
        }
        IntelligenceRuleEvaluationEntity eval = new IntelligenceRuleEvaluationEntity();
        eval.setTenantId(tenantId);
        eval.setRuleId(ruleId);
        eval.setEvaluationMode(normalize(mode, "MANUAL"));
        eval.setEvaluationStatus(triggered ? "TRIGGERED" : "NO_TRIGGER");
        eval.setTriggered(triggered);
        eval.setDetails(message);
        eval = evaluationRepository.save(eval);

        Long alertId = null;
        if (triggered) {
            String dedup = "rule:" + ruleId + ":" + OffsetDateTime.now().toLocalDate();
            if (triggerHistoryRepository.findFirstByTenantIdAndDedupKeyAndStatus(tenantId, dedup, "OPEN").isEmpty()) {
                IntelligenceTriggerHistoryEntity history = new IntelligenceTriggerHistoryEntity();
                history.setTenantId(tenantId);
                history.setRuleId(ruleId);
                history.setEvaluationId(eval.getId());
                history.setDedupKey(dedup);
                history.setStatus("OPEN");
                history.setTriggerMessage(message);
                history = triggerHistoryRepository.save(history);

                IntelligenceAlertEntity alert = new IntelligenceAlertEntity();
                alert.setTenantId(tenantId);
                alert.setRuleId(ruleId);
                alert.setTriggerHistoryId(history.getId());
                alert.setSeverity("MODERATE");
                alert.setTitle(rule.getTitle());
                alert.setDescription(message);
                alert.setStatus("OPEN");
                alert = alertRepository.save(alert);
                alertId = alert.getId();

                IntelligenceGeneratedTaskEntity task = new IntelligenceGeneratedTaskEntity();
                task.setTenantId(tenantId);
                task.setAlertId(alert.getId());
                task.setTaskRef("PH-ALERT-" + alert.getId());
                task.setStatus("DRAFT");
                generatedTaskRepository.save(task);
            }
        }

        return Map.of(
                "rule_id", ruleId,
                "evaluation_id", eval.getId(),
                "triggered", triggered,
                "message", message,
                "alert_id", alertId
        );
    }

    @Transactional(readOnly = true)
    public List<IntelligenceTriggerHistoryEntity> listTriggers(UUID tenantId) {
        return triggerHistoryRepository.findByTenantIdOrderByIdDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<IntelligenceAlertEntity> listAlerts(UUID tenantId) {
        return alertRepository.findByTenantIdOrderByIdDesc(tenantId);
    }

    @Transactional
    public IntelligenceAlertEntity acknowledgeAlert(UUID tenantId, Long alertId) {
        IntelligenceAlertEntity alert = alertRepository.findById(alertId).orElseThrow();
        if (!tenantId.equals(alert.getTenantId())) throw new IllegalArgumentException("Tenant mismatch");
        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedAt(OffsetDateTime.now());
        return alertRepository.save(alert);
    }

    @Transactional
    public IntelligenceAlertEntity resolveAlert(UUID tenantId, Long alertId) {
        IntelligenceAlertEntity alert = alertRepository.findById(alertId).orElseThrow();
        if (!tenantId.equals(alert.getTenantId())) throw new IllegalArgumentException("Tenant mismatch");
        alert.setStatus("RESOLVED");
        alert.setResolvedAt(OffsetDateTime.now());
        return alertRepository.save(alert);
    }

    @Transactional
    public InspectionScheduleEntity scheduleInspection(
            UUID tenantId, String actor, UUID siteId, String inspectionType, OffsetDateTime scheduledAt,
            String responsibleRef, String priority, String reason, String scopeRef, String recurrenceRule,
            String notes, String notificationPreference) {
        InspectionScheduleEntity schedule = new InspectionScheduleEntity();
        schedule.setTenantId(tenantId);
        schedule.setSiteId(siteId);
        schedule.setInspectionType(normalize(inspectionType, "GENERAL"));
        schedule.setScheduledAt(scheduledAt);
        schedule.setResponsibleRef(responsibleRef);
        schedule.setPriority(normalize(priority, "MEDIUM"));
        schedule.setReason(reason);
        schedule.setScopeRef(scopeRef);
        schedule.setRecurrenceRule(recurrenceRule);
        schedule.setNotes(notes);
        schedule.setNotificationPreference(notificationPreference);
        schedule.setCreatedBy(actor != null ? actor : "system");
        return inspectionScheduleRepository.save(schedule);
    }

    @Transactional(readOnly = true)
    public List<InspectionScheduleEntity> listInspectionSchedules(UUID tenantId) {
        return inspectionScheduleRepository.findByTenantIdOrderByScheduledAtDesc(tenantId);
    }

    @Scheduled(cron = "${intelligence.rule.evaluator.cron:0 */15 * * * *}")
    @Transactional
    public void scheduledEvaluation() {
        if (!Boolean.parseBoolean(System.getProperty("intelligence.rule.evaluator.enabled", "true"))) {
            return;
        }
        for (PhAlertRuleEntity rule : ruleRepository.findAll()) {
            if (rule.isActive()) {
                evaluateRule(rule.getTenantId(), rule.getId(), "SCHEDULED");
            }
        }
    }

    @Transactional
    public void onInspectionRecorded(UUID tenantId) {
        for (PhAlertRuleEntity rule : ruleRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)) {
            if (rule.isActive()) {
                evaluateRule(tenantId, rule.getId(), "EVENT_DRIVEN");
            }
        }
    }

    private String normalize(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private double asDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return value == null ? 0 : Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }

    private boolean matches(double observed, String op, double threshold) {
        String normalized = normalize(op, "GT");
        return switch (normalized) {
            case "GTE" -> observed >= threshold;
            case "LT" -> observed < threshold;
            case "LTE" -> observed <= threshold;
            case "EQ" -> Double.compare(observed, threshold) == 0;
            default -> observed > threshold;
        };
    }
}
