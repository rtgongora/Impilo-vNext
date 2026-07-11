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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Versioned, configurable regulatory rules (HPA-2017-V1 — the CURRENT
 * operative HPA manual, confirmed in force by the programme owner 2026-07-11,
 * effective until formally superseded).
 *
 * <p>Faithful representations of the manual seed ACTIVE; the engine consults
 * an ACTIVE rule first, then falls back to the latest pending rule (so
 * behaviour is visible and consistent), and finally to conservative code
 * defaults. All values remain versioned and configurable so future amendments
 * apply without code changes; fee amounts live in separate statutory
 * instruments and are never hard-coded.</p>
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

    /** Certificate validity cycle in months (used when the rule is not calendar-year mode). */
    @Transactional(readOnly = true)
    public int renewalCycleMonths() {
        Map<String, Object> value = configValue("RENEWAL_CYCLE_MONTHS");
        if (value != null && value.get("months") instanceof Number n && n.intValue() > 0) {
            return n.intValue();
        }
        return DEFAULT_RENEWAL_CYCLE_MONTHS;
    }

    /**
     * Certificate expiry for an issue date. Manual §3.1: the Registration
     * Certificate is valid for a calendar year, up to 31 December of that year
     * ({@code mode: CALENDAR_YEAR}); a rolling month cycle applies only if a
     * future rule version configures one.
     */
    @Transactional(readOnly = true)
    public LocalDate certificateExpiryFrom(LocalDate issueDate) {
        Map<String, Object> value = configValue("RENEWAL_CYCLE_MONTHS");
        if (value != null && "CALENDAR_YEAR".equals(value.get("mode"))) {
            return LocalDate.of(issueDate.getYear(), 12, 31);
        }
        int months = value != null && value.get("months") instanceof Number n && n.intValue() > 0
                ? n.intValue() : DEFAULT_RENEWAL_CYCLE_MONTHS;
        return issueDate.plusMonths(months);
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
