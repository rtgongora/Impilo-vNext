package zw.gov.mohcc.impilo.rito.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.persistence.entity.EscalationRuleEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.SlaPolicyEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.EscalationRuleRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.SlaPolicyRepository;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * CRUD service for the Rito triage configuration aggregates: SLA policies
 * ({@code rit_sla_policy}) and escalation rules ({@code rit_escalation_rule}). These drive how
 * {@code CaseService} computes due dates and how cases escalate — they are configuration, not
 * runtime case state, so this service stays deliberately thin.
 */
@Service
public class ConfigService {

    private final SlaPolicyRepository slaPolicyRepository;
    private final EscalationRuleRepository escalationRuleRepository;

    public ConfigService(SlaPolicyRepository slaPolicyRepository,
                         EscalationRuleRepository escalationRuleRepository) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.escalationRuleRepository = escalationRuleRepository;
    }

    @Transactional
    public SlaPolicyEntity createSlaPolicy(UUID tenantId, String policyKey, String name,
                                           String appliesToCaseType, String appliesToSeverity,
                                           Integer acknowledgeWithinMinutes, Integer resolveWithinMinutes,
                                           Integer escalateAfterMinutes) {
        String key = require(policyKey, "policyKey");
        if (slaPolicyRepository.findByTenantIdAndPolicyKey(tenantId, key).isPresent()) {
            throw new IllegalArgumentException("SLA policy already exists for key: " + key);
        }
        SlaPolicyEntity p = new SlaPolicyEntity();
        p.setTenantId(tenantId);
        p.setPolicyKey(key);
        p.setName(require(name, "name"));
        p.setAppliesToCaseType(appliesToCaseType);
        p.setAppliesToSeverity(appliesToSeverity);
        p.setAcknowledgeWithinMinutes(acknowledgeWithinMinutes);
        p.setResolveWithinMinutes(resolveWithinMinutes);
        p.setEscalateAfterMinutes(escalateAfterMinutes);
        p.setActive(Boolean.TRUE);
        return slaPolicyRepository.save(p);
    }

    @Transactional
    public SlaPolicyEntity updateSlaPolicy(UUID tenantId, UUID id, Boolean active,
                                           Integer acknowledgeWithinMinutes, Integer resolveWithinMinutes,
                                           Integer escalateAfterMinutes) {
        SlaPolicyEntity p = slaPolicyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("SLA policy not found: " + id));
        if (active != null) {
            p.setActive(active);
        }
        if (acknowledgeWithinMinutes != null) {
            p.setAcknowledgeWithinMinutes(acknowledgeWithinMinutes);
        }
        if (resolveWithinMinutes != null) {
            p.setResolveWithinMinutes(resolveWithinMinutes);
        }
        if (escalateAfterMinutes != null) {
            p.setEscalateAfterMinutes(escalateAfterMinutes);
        }
        return slaPolicyRepository.save(p);
    }

    @Transactional(readOnly = true)
    public List<SlaPolicyEntity> listSlaPolicies(UUID tenantId) {
        return slaPolicyRepository.findByTenantIdAndActiveTrue(tenantId);
    }

    @Transactional
    public EscalationRuleEntity createEscalationRule(UUID tenantId, String ruleKey, String name,
                                                     String triggerType, Map<String, Object> condition,
                                                     String escalateToRole, String escalateToLevel,
                                                     String notifyTemplateKey) {
        String key = require(ruleKey, "ruleKey");
        if (escalationRuleRepository.existsByTenantIdAndRuleKey(tenantId, key)) {
            throw new IllegalArgumentException("Escalation rule already exists for key: " + key);
        }
        EscalationRuleEntity e = new EscalationRuleEntity();
        e.setTenantId(tenantId);
        e.setRuleKey(key);
        e.setName(require(name, "name"));
        e.setTriggerType(require(triggerType, "triggerType"));
        e.setConditionJson(JsonUtil.toJson(condition == null ? Map.of() : condition));
        e.setEscalateToRole(escalateToRole);
        e.setEscalateToLevel(escalateToLevel);
        e.setNotifyTemplateKey(notifyTemplateKey);
        e.setActive(Boolean.TRUE);
        return escalationRuleRepository.save(e);
    }

    @Transactional(readOnly = true)
    public List<EscalationRuleEntity> listEscalationRules(UUID tenantId) {
        return escalationRuleRepository.findByTenantIdAndActiveTrue(tenantId);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }
}
