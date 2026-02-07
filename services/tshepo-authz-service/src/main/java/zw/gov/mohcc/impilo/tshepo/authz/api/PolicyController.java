package zw.gov.mohcc.impilo.tshepo.authz.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.authz.dto.PolicyRuleRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.PolicyRuleResponse;
import zw.gov.mohcc.impilo.tshepo.authz.service.PolicyManagementService;

import java.util.List;
import java.util.UUID;

/**
 * Policy rule CRUD management endpoints.
 *
 * <p>Used by platform admins to define and manage ABAC/RBAC policy rules.
 * All write operations invalidate the Redis policy cache for the affected
 * tenant.</p>
 */
@RestController
@RequestMapping("/v1/policies")
public class PolicyController {

    private static final Logger log = LoggerFactory.getLogger(PolicyController.class);

    private final PolicyManagementService policyService;

    public PolicyController(PolicyManagementService policyService) {
        this.policyService = policyService;
    }

    /**
     * List all policy rules for a tenant (including inactive).
     */
    @GetMapping
    public ResponseEntity<List<PolicyRuleResponse>> listRules(
            @RequestHeader("x-tenant-id") UUID tenantId) {
        List<PolicyRuleResponse> rules = policyService.listRules(tenantId);
        return ResponseEntity.ok(rules);
    }

    /**
     * Create a new policy rule.
     */
    @PostMapping
    public ResponseEntity<PolicyRuleResponse> createRule(
            @Valid @RequestBody PolicyRuleRequest request) {
        PolicyRuleResponse response = policyService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing policy rule.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PolicyRuleResponse> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody PolicyRuleRequest request) {
        try {
            PolicyRuleResponse response = policyService.updateRule(id, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Delete (deactivate) a policy rule.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(
            @PathVariable Long id,
            @RequestHeader("x-tenant-id") UUID tenantId) {
        try {
            policyService.deleteRule(id, tenantId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
}
