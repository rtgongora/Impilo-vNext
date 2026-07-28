package zw.gov.mohcc.impilo.tshepo.authz.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.PolicyRuleRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Self-reported enforcement posture (Phase D, D6). What this service can see
 * about ITSELF only — the cross-system view the PO's brief asks for
 * (Envoy ext_authz filter presence, whether protected services are directly
 * externally exposed) needs Envoy admin-API and k8s-API introspection this
 * service has no access to, and is honestly out of scope here. This is the
 * seed a future ops/BFF aggregator composes from every service's own
 * self-report, not the full cross-system gate on its own.
 *
 * <p>No secrets, no config values that could aid an attacker — only booleans,
 * counts and mode names, matching the brief's "without exposing sensitive
 * configuration."</p>
 */
@RestController
@RequestMapping("/v1/internal/security-readiness")
public class SecurityReadinessController {

    private final AuthzProperties properties;
    private final PolicyRuleRepository policyRuleRepository;

    public SecurityReadinessController(AuthzProperties properties, PolicyRuleRepository policyRuleRepository) {
        this.properties = properties;
        this.policyRuleRepository = policyRuleRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> readiness(@RequestHeader("x-tenant-id") UUID tenantId) {
        Map<String, Object> report = new LinkedHashMap<>();

        String workContextMode = properties.getWorkContextMode();
        String confidentialityMode = properties.getConfidentialityMode();
        boolean workContextEnforcing = "ENFORCE".equalsIgnoreCase(workContextMode);

        report.put("service", "tshepo-authz-service");
        report.put("workContextMode", workContextMode);
        report.put("workContextEnforcing", workContextEnforcing);
        report.put("confidentialityMode", confidentialityMode);

        long totalRules = policyRuleRepository.countByTenantId(tenantId);
        long activeRules = policyRuleRepository.countByTenantIdAndActiveTrue(tenantId);
        report.put("policyRuleCountTotal", totalRules);
        report.put("policyRuleCountActive", activeRules);
        report.put("policyBundleLoaded", totalRules > 0);

        // V055 WorkMode boundary rules (Phase B) — active=false is the current,
        // intentional SHADOW state; this field exists so a deployment gate can
        // assert "boundary rules are active" once the D7 cutover flips them,
        // rather than assuming.
        long workModeRulesTotal = policyRuleRepository.countByTenantIdAndNameStartingWith(tenantId, "work-mode-");
        long workModeRulesActive = policyRuleRepository.countByTenantIdAndNameStartingWithAndActiveTrue(tenantId, "work-mode-");
        report.put("workModeBoundaryRuleCountTotal", workModeRulesTotal);
        report.put("workModeBoundaryRuleCountActive", workModeRulesActive);
        report.put("workModeBoundaryRulesEnforcing", workModeRulesTotal > 0 && workModeRulesActive == workModeRulesTotal);

        // ROM cross-council isolation rows (V045/V046/V047) — same shape, same reason.
        long romRulesTotal = policyRuleRepository.countByTenantIdAndNameStartingWith(tenantId, "rom-");
        long romRulesActive = policyRuleRepository.countByTenantIdAndNameStartingWithAndActiveTrue(tenantId, "rom-");
        report.put("regulatoryIsolationRuleCountTotal", romRulesTotal);
        report.put("regulatoryIsolationRuleCountActive", romRulesActive);
        report.put("regulatoryIsolationRulesEnforcing", romRulesTotal > 0 && romRulesActive == romRulesTotal);

        // Aggregate verdict: is THIS service's own posture "enforcing" by every
        // signal it can see? A deployment gate should still fail if
        // workContextEnforcing is false even when every rule above is active —
        // the mode-gated role folding (Phase B) is inert without it.
        boolean selfReportedEnforcing = workContextEnforcing
                && (Boolean) report.get("workModeBoundaryRulesEnforcing")
                && (Boolean) report.get("regulatoryIsolationRulesEnforcing");
        report.put("selfReportedEnforcing", selfReportedEnforcing);

        report.put("note", "This is a self-report. It does NOT confirm Envoy ext_authz is wired on the "
                + "ingress path, that failure_mode_allow=false, or that this service is unreachable "
                + "except through the gateway — those require infrastructure-level checks this "
                + "endpoint cannot perform.");

        return ResponseEntity.ok(report);
    }
}
