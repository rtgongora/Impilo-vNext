package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryRuleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.RegulatoryRuleRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Versioned, configurable regulatory rules (HPA-2017-V1 baseline).
 *
 * <p>Rules seeded from the 2017 manual are {@code PENDING_REGULATOR_APPROVAL}
 * and treated as <em>provisional configuration</em>: the engine consults an
 * ACTIVE rule first, then falls back to the latest pending rule (so behaviour
 * is visible and consistent), and finally to conservative code defaults. No
 * 2017 fee, deadline or category is enforced as timeless law.</p>
 */
@Service
public class RegulatoryRuleService {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryRuleService.class);

    static final int DEFAULT_CRITICAL_DAYS = 14;
    static final int DEFAULT_STANDARD_DAYS = 30;
    static final int DEFAULT_RENEWAL_CYCLE_MONTHS = 12;
    static final int DEFAULT_RENEWAL_DUE_WINDOW_DAYS = 90;

    private final RegulatoryRuleRepository ruleRepository;

    public RegulatoryRuleService(RegulatoryRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    /** Remediation window in days for a finding severity (CRITICAL/MAJOR/MINOR/OBSERVATION). */
    @Transactional(readOnly = true)
    public int remediationWindowDays(String severity, boolean critical) {
        String key = critical ? "CRITICAL" : (severity == null ? "MAJOR" : severity.toUpperCase());
        Map<String, Object> value = configValue("REMEDIATION_WINDOW_DAYS");
        if (value != null && value.get(key) instanceof Number n) {
            return n.intValue();
        }
        return critical ? DEFAULT_CRITICAL_DAYS : DEFAULT_STANDARD_DAYS;
    }

    /** Certificate validity cycle in months (configurable; 2017 baseline = annual). */
    @Transactional(readOnly = true)
    public int renewalCycleMonths() {
        Map<String, Object> value = configValue("RENEWAL_CYCLE_MONTHS");
        if (value != null && value.get("months") instanceof Number n && n.intValue() > 0) {
            return n.intValue();
        }
        return DEFAULT_RENEWAL_CYCLE_MONTHS;
    }

    /** Days before certificate expiry at which a facility enters RENEWAL_DUE. */
    @Transactional(readOnly = true)
    public int renewalDueWindowDays() {
        Map<String, Object> value = configValue("RENEWAL_DUE_WINDOW_DAYS");
        if (value != null && value.get("days") instanceof Number n && n.intValue() > 0) {
            return n.intValue();
        }
        return DEFAULT_RENEWAL_DUE_WINDOW_DAYS;
    }

    @Transactional(readOnly = true)
    public List<RegulatoryRuleEntity> listRules() {
        return ruleRepository.findAllByOrderByCodeAscVersionDesc();
    }

    /** Regulator approval of a rule version — an explicit, audited human action. */
    @Transactional
    public RegulatoryRuleEntity approveRule(UUID ruleId) {
        TrustContext ctx = TrustContextHolder.require();
        RegulatoryRuleEntity rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + ruleId));
        rule.setStatus("ACTIVE");
        rule.setApprovedBy(ctx.actorId());
        rule.setApprovedAt(Instant.now());
        log.info("Regulatory rule {} v{} approved by {}", rule.getCode(), rule.getVersion(), ctx.actorId());
        return ruleRepository.save(rule);
    }

    private Map<String, Object> configValue(String code) {
        Optional<RegulatoryRuleEntity> active = ruleRepository.findFirstByCodeAndStatusOrderByVersionDesc(code, "ACTIVE");
        Optional<RegulatoryRuleEntity> rule = active.isPresent()
                ? active
                : ruleRepository.findFirstByCodeOrderByVersionDesc(code);
        return rule.map(RegulatoryRuleEntity::getValueJson).orElse(null);
    }
}
