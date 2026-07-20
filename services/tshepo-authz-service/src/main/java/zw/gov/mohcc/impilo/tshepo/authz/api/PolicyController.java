package zw.gov.mohcc.impilo.tshepo.authz.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.tshepo.authz.dto.PolicyRuleRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.PolicyRuleResponse;
import zw.gov.mohcc.impilo.tshepo.authz.service.PolicyManagementService;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionAssuranceRouter;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionInfo;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionValidationException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Policy rule CRUD management endpoints.
 *
 * <p>Used by platform admins to define and manage ABAC/RBAC policy rules.
 * All write operations invalidate the Redis policy cache for the affected
 * tenant.</p>
 *
 * <p><b>Runtime mutation is code-gated</b> ({@link #requireAdmin}): a POST/PUT/DELETE
 * must present a valid session bearer carrying an admin role, or be an internal
 * SYSTEM service call. The primary policy channel remains the seeded Flyway
 * migrations; this API is the break-glass admin path and must not be open.</p>
 */
@RestController
@RequestMapping("/v1/policies")
public class PolicyController {

    private static final Logger log = LoggerFactory.getLogger(PolicyController.class);

    /** Roles permitted to mutate policy rules at runtime. */
    private static final Set<String> ADMIN_ROLES =
            Set.of("SYSTEM_ADMIN", "PLATFORM_ORIGIN_ADMINISTRATOR", "TRUST_ADMIN");

    private final PolicyManagementService policyService;
    private final SessionAssuranceRouter sessionRouter;

    public PolicyController(PolicyManagementService policyService,
                            SessionAssuranceRouter sessionRouter) {
        this.policyService = policyService;
        this.sessionRouter = sessionRouter;
    }

    /**
     * Gate a runtime policy mutation. Accepts either (a) a valid session bearer whose
     * roles include an admin role, or (b) an internal SYSTEM service-to-service call.
     * Anything else → 403. Fail-closed: a validation error denies.
     */
    private void requireAdmin(String authorization, String actorType) {
        // Internal automation / seed replay arrives as a SYSTEM actor (the public lane strips
        // actor headers, so this cannot be spoofed by an anonymous external caller).
        if ("SYSTEM".equalsIgnoreCase(actorType)) {
            return;
        }
        if (authorization != null && !authorization.isBlank()) {
            try {
                SessionInfo session = sessionRouter.validateSession(authorization);
                if (session.roles() != null
                        && session.roles().stream().anyMatch(ADMIN_ROLES::contains)) {
                    return;
                }
                log.warn("Policy mutation denied: actor={} lacks an admin role {}",
                        session.actorId(), ADMIN_ROLES);
            } catch (SessionValidationException e) {
                log.warn("Policy mutation denied: session invalid — {}", e.getMessage());
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Policy mutation requires an admin session or internal SYSTEM caller");
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
            @Valid @RequestBody PolicyRuleRequest request,
            @RequestHeader(value = "authorization", required = false) String authorization,
            @RequestHeader(value = "x-actor-type", required = false) String actorType) {
        requireAdmin(authorization, actorType);
        PolicyRuleResponse response = policyService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing policy rule.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PolicyRuleResponse> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody PolicyRuleRequest request,
            @RequestHeader(value = "authorization", required = false) String authorization,
            @RequestHeader(value = "x-actor-type", required = false) String actorType) {
        requireAdmin(authorization, actorType);
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
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader(value = "authorization", required = false) String authorization,
            @RequestHeader(value = "x-actor-type", required = false) String actorType) {
        requireAdmin(authorization, actorType);
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
