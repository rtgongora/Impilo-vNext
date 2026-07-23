package zw.gov.mohcc.impilo.clinical.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.persistence.entity.RuleDefinitionEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.RuleDefinitionRepository;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The versioned-rules governance seam (OF-B3): applies {@code clinical.rule_definitions}
 * metadata over the deterministic engine's alerts. A definition row for an alert code may
 * override severity / interruptive / override-allowed, and its effective window retires a
 * rule (suppressed with a log — never silently). Rules with no row pass through unchanged,
 * so absence of governance data degrades to today's behaviour, not to silence.
 *
 * <p>Fail-open on read errors: an unreadable governance table must never suppress a
 * safety alert — the engine's deterministic output stands.</p>
 */
@Service
public class RuleGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(RuleGovernanceService.class);

    private final RuleDefinitionRepository repository;

    public RuleGovernanceService(RuleDefinitionRepository repository) {
        this.repository = repository;
    }

    public List<RuleAlert> apply(List<RuleAlert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return alerts;
        }
        Map<String, RuleDefinitionEntity> byCode;
        try {
            byCode = new HashMap<>();
            for (RuleDefinitionEntity def : repository.findAll()) {
                byCode.put(def.getCode(), def);
            }
        } catch (Exception e) {
            log.warn("Rule governance table unreadable — engine output stands unmodified: {}", e.getMessage());
            return alerts;
        }
        if (byCode.isEmpty()) {
            return alerts;
        }

        LocalDate today = LocalDate.now();
        List<RuleAlert> out = new ArrayList<>(alerts.size());
        for (RuleAlert alert : alerts) {
            RuleDefinitionEntity def = byCode.get(alert.code());
            if (def == null) {
                out.add(alert);
                continue;
            }
            if (!def.activeOn(today)) {
                log.info("Rule {} suppressed by governance (outside effective window {}..{})",
                        alert.code(), def.getEffectiveStart(), def.getEffectiveEnd());
                continue;
            }
            String severity = def.getSeverity() != null && !def.getSeverity().isBlank()
                    ? def.getSeverity() : alert.severity();
            boolean interruptive = def.getInterruptive() != null ? def.getInterruptive() : alert.interruptive();
            boolean overrideAllowed = def.getOverrideAllowed() != null
                    ? def.getOverrideAllowed() : alert.overrideAllowed();
            if (severity.equals(alert.severity()) && interruptive == alert.interruptive()
                    && overrideAllowed == alert.overrideAllowed()) {
                out.add(alert);
            } else {
                out.add(new RuleAlert(alert.code(), severity, alert.message(),
                        alert.explanation(), interruptive, overrideAllowed));
            }
        }
        return out;
    }
}
