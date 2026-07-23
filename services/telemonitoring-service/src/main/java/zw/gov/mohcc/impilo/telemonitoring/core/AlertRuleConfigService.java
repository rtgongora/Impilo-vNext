package zw.gov.mohcc.impilo.telemonitoring.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.AlertRuleEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.AlertRuleRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Effective alert-engine configuration (OF-B26). The band rules themselves live in the
 * plan's current threshold-profile version (see the V005 design decision);
 * {@code tm_alert_rules} rows override only windows/tolerances/rate-limits. Field-level
 * resolution: plan override → programme default → engine defaults.
 */
@Service
public class AlertRuleConfigService {

    // Engine defaults (§14.6 defaults are profile-overridable; these are the honest floor).
    static final int DEFAULT_REPEAT_ABNORMAL_COUNT = 2;         // trigger 5: second breach despite guided repeat
    static final int DEFAULT_REPEAT_ABNORMAL_WINDOW_MINUTES = 240;
    static final int DEFAULT_AMBER_RESPONSE_MINUTES = 240;      // 4h
    static final int DEFAULT_RED_RESPONSE_MINUTES = 60;         // 1h
    static final int DEFAULT_EMERGENCY_RESPONSE_MINUTES = 15;
    static final int DEFAULT_OFFLINE_TOLERANCE_HOURS = 72;      // journey #65: three days
    static final int DEFAULT_DEVICE_QUALITY_MIN_COUNT = 3;      // trigger 9: persistent, not one-off

    /** Resolved, never-null effective configuration. */
    public record EffectiveAlertConfig(
            int repeatAbnormalCount,
            int repeatAbnormalWindowMinutes,
            int amberResponseMinutes,
            int redResponseMinutes,
            int emergencyResponseMinutes,
            int offlineToleranceHours,
            int deviceQualityMinCount,
            boolean deviceAlertsEnabled) {

        public int responseMinutesFor(zw.gov.mohcc.impilo.telemonitoring.domain.AlertSeverity severity) {
            return switch (severity) {
                case AMBER -> amberResponseMinutes;
                case RED -> redResponseMinutes;
                case EMERGENCY -> emergencyResponseMinutes;
            };
        }
    }

    private final AlertRuleRepository ruleRepository;

    public AlertRuleConfigService(AlertRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public EffectiveAlertConfig resolve(UUID tenantId, UUID planId, String programmeCode) {
        AlertRuleEntity plan = planId != null
                ? ruleRepository.findByPlanId(planId).orElse(null) : null;
        AlertRuleEntity programme = programmeCode != null && tenantId != null
                ? ruleRepository.findFirstByTenantIdAndProgrammeCodeAndPlanIdIsNull(tenantId, programmeCode).orElse(null)
                : null;
        return new EffectiveAlertConfig(
                pick(plan, programme, AlertRuleEntity::getRepeatAbnormalCount, DEFAULT_REPEAT_ABNORMAL_COUNT),
                pick(plan, programme, AlertRuleEntity::getRepeatAbnormalWindowMinutes, DEFAULT_REPEAT_ABNORMAL_WINDOW_MINUTES),
                pick(plan, programme, AlertRuleEntity::getAmberResponseMinutes, DEFAULT_AMBER_RESPONSE_MINUTES),
                pick(plan, programme, AlertRuleEntity::getRedResponseMinutes, DEFAULT_RED_RESPONSE_MINUTES),
                pick(plan, programme, AlertRuleEntity::getEmergencyResponseMinutes, DEFAULT_EMERGENCY_RESPONSE_MINUTES),
                pick(plan, programme, AlertRuleEntity::getOfflineToleranceHours, DEFAULT_OFFLINE_TOLERANCE_HOURS),
                pick(plan, programme, AlertRuleEntity::getDeviceQualityMinCount, DEFAULT_DEVICE_QUALITY_MIN_COUNT),
                Optional.ofNullable(plan).map(AlertRuleEntity::isDeviceAlertsEnabled)
                        .orElseGet(() -> programme == null || programme.isDeviceAlertsEnabled()));
    }

    private static int pick(AlertRuleEntity plan, AlertRuleEntity programme,
                            java.util.function.Function<AlertRuleEntity, Integer> getter, int fallback) {
        if (plan != null && getter.apply(plan) != null) return getter.apply(plan);
        if (programme != null && getter.apply(programme) != null) return getter.apply(programme);
        return fallback;
    }
}
