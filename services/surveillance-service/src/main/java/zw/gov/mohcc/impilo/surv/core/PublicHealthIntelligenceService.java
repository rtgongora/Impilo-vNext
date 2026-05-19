package zw.gov.mohcc.impilo.surv.core;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.PhAlertRuleEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.PhDataQualityIssueEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.PhDataSourceStatusEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.PhAlertRuleRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.PhDataQualityIssueRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.PhDataSourceStatusRepository;

@Service
public class PublicHealthIntelligenceService {
    private final PhAlertRuleRepository alertRuleRepository;
    private final PhDataSourceStatusRepository dataSourceStatusRepository;
    private final PhDataQualityIssueRepository dataQualityIssueRepository;

    public PublicHealthIntelligenceService(
            PhAlertRuleRepository alertRuleRepository,
            PhDataSourceStatusRepository dataSourceStatusRepository,
            PhDataQualityIssueRepository dataQualityIssueRepository) {
        this.alertRuleRepository = alertRuleRepository;
        this.dataSourceStatusRepository = dataSourceStatusRepository;
        this.dataQualityIssueRepository = dataQualityIssueRepository;
    }

    @Transactional(readOnly = true)
    public List<PhAlertRuleEntity> listAlertRules(UUID tenantId) {
        return alertRuleRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
    }

    @Transactional
    public PhAlertRuleEntity createAlertRule(UUID tenantId, String actorId, String ruleKey, String title, String description, double thresholdValue, String comparisonOperator) {
        PhAlertRuleEntity entity = new PhAlertRuleEntity();
        entity.setTenantId(tenantId);
        entity.setCreatedBy(actorId);
        entity.setRuleKey(normalizeKey(ruleKey, title));
        entity.setTitle(textOrDefault(title, "Operational alert rule"));
        entity.setDescription(description);
        entity.setThresholdValue(thresholdValue);
        entity.setComparisonOperator(textOrDefault(comparisonOperator, "GT").toUpperCase(Locale.ROOT));
        entity.setActive(true);
        return alertRuleRepository.save(entity);
    }

    @Transactional
    public PhAlertRuleEntity toggleAlertRule(UUID tenantId, Long id, boolean active) {
        PhAlertRuleEntity entity = alertRuleRepository.findById(id)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Alert rule not found"));
        entity.setActive(active);
        return alertRuleRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<PhDataSourceStatusEntity> listDataSources(UUID tenantId) {
        return dataSourceStatusRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
    }

    @Transactional
    public PhDataSourceStatusEntity upsertDataSource(UUID tenantId, String actorId, String sourceKey, String sourceName, String healthStatus, long latencySeconds, int errorCount24h, String details) {
        PhDataSourceStatusEntity entity = dataSourceStatusRepository.findByTenantIdAndSourceKey(tenantId, normalizeKey(sourceKey, sourceName))
                .orElseGet(PhDataSourceStatusEntity::new);
        entity.setTenantId(tenantId);
        entity.setCreatedBy(actorId);
        entity.setSourceKey(normalizeKey(sourceKey, sourceName));
        entity.setSourceName(textOrDefault(sourceName, sourceKey));
        entity.setHealthStatus(textOrDefault(healthStatus, "HEALTHY").toUpperCase(Locale.ROOT));
        entity.setLatencySeconds(Math.max(0, latencySeconds));
        entity.setErrorCount24h(Math.max(0, errorCount24h));
        entity.setDetails(details);
        entity.setLastSyncAt(OffsetDateTime.now());
        return dataSourceStatusRepository.save(entity);
    }

    @Transactional
    public PhDataSourceStatusEntity heartbeatDataSource(UUID tenantId, Long id, String healthStatus, long latencySeconds, int errorCount24h, String details) {
        PhDataSourceStatusEntity entity = dataSourceStatusRepository.findById(id)
                .filter(s -> tenantId.equals(s.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Data source not found"));
        entity.setHealthStatus(textOrDefault(healthStatus, "HEALTHY").toUpperCase(Locale.ROOT));
        entity.setLatencySeconds(Math.max(0, latencySeconds));
        entity.setErrorCount24h(Math.max(0, errorCount24h));
        entity.setDetails(details);
        entity.setLastSyncAt(OffsetDateTime.now());
        return dataSourceStatusRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<PhDataQualityIssueEntity> listDataQualityIssues(UUID tenantId) {
        return dataQualityIssueRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
    }

    @Transactional
    public PhDataQualityIssueEntity createDataQualityIssue(UUID tenantId, String actorId, String issueType, String severity, String title, String description, String scopeRef) {
        PhDataQualityIssueEntity entity = new PhDataQualityIssueEntity();
        entity.setTenantId(tenantId);
        entity.setCreatedBy(actorId);
        entity.setIssueType(textOrDefault(issueType, "REPORTING_COMPLETENESS"));
        entity.setSeverity(textOrDefault(severity, "MEDIUM").toUpperCase(Locale.ROOT));
        entity.setTitle(textOrDefault(title, "Data quality issue"));
        entity.setDescription(description);
        entity.setScopeRef(scopeRef);
        entity.setStatus("OPEN");
        return dataQualityIssueRepository.save(entity);
    }

    @Transactional
    public PhDataQualityIssueEntity resolveDataQualityIssue(UUID tenantId, Long id, String resolutionNotes) {
        PhDataQualityIssueEntity entity = dataQualityIssueRepository.findById(id)
                .filter(i -> tenantId.equals(i.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Data quality issue not found"));
        entity.setStatus("RESOLVED");
        entity.setResolvedAt(OffsetDateTime.now());
        entity.setResolutionNotes(resolutionNotes);
        return dataQualityIssueRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> intelligenceKpis(UUID tenantId) {
        long activeRules = alertRuleRepository.countByTenantIdAndActiveTrue(tenantId);
        long staleSources = dataSourceStatusRepository.countByTenantIdAndLastSyncAtBefore(tenantId, OffsetDateTime.now().minusHours(24));
        long openDataQualityIssues = dataQualityIssueRepository.countByTenantIdAndStatusIgnoreCase(tenantId, "OPEN");
        return Map.of(
                "active_alert_rules", activeRules,
                "stale_data_sources", staleSources,
                "open_data_quality_issues", openDataQualityIssues);
    }

    private String normalizeKey(String raw, String fallbackRaw) {
        String base = textOrDefault(raw, fallbackRaw).toLowerCase(Locale.ROOT).trim();
        return base.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private String textOrDefault(String raw, String fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        return raw.trim();
    }
}
