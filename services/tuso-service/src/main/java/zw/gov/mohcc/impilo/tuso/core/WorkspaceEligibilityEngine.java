package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceRuleEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Engine that evaluates whether a provider is eligible to work in a specific workspace.
 *
 * <p>A workspace's rules define required actor_type, roles, and privileges.
 * A provider is eligible if they match ALL required rules.
 * If a workspace has no rules, any provider is eligible.</p>
 */
@Service
public class WorkspaceEligibilityEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceEligibilityEngine.class);

    /**
     * Calculate whether a provider is eligible for a single workspace.
     *
     * @param providerActorType the provider's actor type (e.g. "DOCTOR", "NURSE")
     * @param providerRoles     the set of roles the provider holds
     * @param providerPrivileges the set of privileges the provider holds
     * @param rules             the workspace eligibility rules
     * @return true if the provider matches all required rules, false otherwise
     */
    public boolean calculateEligibility(String providerActorType,
                                         Set<String> providerRoles,
                                         Set<String> providerPrivileges,
                                         List<WorkspaceRuleEntity> rules) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }

        List<WorkspaceRuleEntity> requiredRules = rules.stream()
                .filter(WorkspaceRuleEntity::isRequired)
                .toList();

        if (requiredRules.isEmpty()) {
            return true;
        }

        for (WorkspaceRuleEntity rule : requiredRules) {
            if (!matchesRule(providerActorType, providerRoles, providerPrivileges, rule)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Evaluate a list of workspaces and return eligibility results with reasons for ineligibility.
     *
     * @param providerActorType  the provider's actor type
     * @param providerRoles      the set of roles the provider holds
     * @param providerPrivileges the set of privileges the provider holds
     * @param workspaces         the workspaces to evaluate
     * @param rulesPerWorkspace  rules grouped by workspace (parallel list with workspaces)
     * @return list of eligibility results
     */
    public List<EligibilityResult> evaluateAll(String providerActorType,
                                                Set<String> providerRoles,
                                                Set<String> providerPrivileges,
                                                List<WorkspaceEntity> workspaces,
                                                List<List<WorkspaceRuleEntity>> rulesPerWorkspace) {
        List<EligibilityResult> results = new ArrayList<>();

        for (int i = 0; i < workspaces.size(); i++) {
            WorkspaceEntity workspace = workspaces.get(i);
            List<WorkspaceRuleEntity> rules = rulesPerWorkspace.get(i);
            List<String> ineligibilityReasons = new ArrayList<>();

            if (rules == null || rules.isEmpty()) {
                results.add(new EligibilityResult(workspace, true, Collections.emptyList()));
                continue;
            }

            List<WorkspaceRuleEntity> requiredRules = rules.stream()
                    .filter(WorkspaceRuleEntity::isRequired)
                    .toList();

            if (requiredRules.isEmpty()) {
                results.add(new EligibilityResult(workspace, true, Collections.emptyList()));
                continue;
            }

            boolean eligible = true;
            for (WorkspaceRuleEntity rule : requiredRules) {
                if (!matchesRule(providerActorType, providerRoles, providerPrivileges, rule)) {
                    eligible = false;
                    ineligibilityReasons.add(buildIneligibilityReason(rule));
                }
            }

            results.add(new EligibilityResult(workspace, eligible, ineligibilityReasons));
        }

        log.debug("Evaluated eligibility for {} workspaces: {} eligible",
                workspaces.size(), results.stream().filter(EligibilityResult::eligible).count());

        return results;
    }

    private boolean matchesRule(String providerActorType,
                                 Set<String> providerRoles,
                                 Set<String> providerPrivileges,
                                 WorkspaceRuleEntity rule) {
        String ruleType = rule.getRuleType();

        return switch (ruleType) {
            case "ACTOR_TYPE" -> {
                String requiredActorType = rule.getActorType();
                yield requiredActorType == null || requiredActorType.equalsIgnoreCase(providerActorType);
            }
            case "ROLE" -> {
                String requiredRole = rule.getRole();
                yield requiredRole == null || providerRoles.contains(requiredRole);
            }
            case "PRIVILEGE" -> {
                String requiredPrivilege = rule.getPrivilege();
                yield requiredPrivilege == null || providerPrivileges.contains(requiredPrivilege);
            }
            default -> {
                log.warn("Unknown workspace rule type: {}", ruleType);
                yield true;
            }
        };
    }

    private String buildIneligibilityReason(WorkspaceRuleEntity rule) {
        return switch (rule.getRuleType()) {
            case "ACTOR_TYPE" -> "Requires actor type: " + rule.getActorType();
            case "ROLE" -> "Requires role: " + rule.getRole();
            case "PRIVILEGE" -> "Requires privilege: " + rule.getPrivilege();
            default -> "Unmet rule: " + rule.getRuleType();
        };
    }

    /**
     * Result of an eligibility evaluation for a single workspace.
     */
    public record EligibilityResult(
            WorkspaceEntity workspace,
            boolean eligible,
            List<String> ineligibilityReasons
    ) {}
}
