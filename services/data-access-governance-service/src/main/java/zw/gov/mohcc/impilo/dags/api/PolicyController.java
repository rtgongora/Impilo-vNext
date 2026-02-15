package zw.gov.mohcc.impilo.dags.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.dags.core.PolicyEffect;
import zw.gov.mohcc.impilo.dags.core.PolicyService;
import zw.gov.mohcc.impilo.dags.persistence.entity.PolicyEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

@RestController
@RequestMapping("/internal/v1/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PolicyEntity>> createPolicy(@RequestBody CreatePolicyRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        PolicyEntity policy = policyService.createPolicy(
                ctx.tenantId(), ctx.actorId(), ctx.correlationId(),
                request.name(), request.description(),
                request.resourceType(), request.conditions(),
                request.effect());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(policy, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PolicyEntity>>> listPolicies() {
        TrustContext ctx = TrustContextHolder.require();

        List<PolicyEntity> policies = policyService.listPolicies(ctx.tenantId());

        return ResponseEntity.ok(
                ApiResponse.ok(policies, ctx.correlationId().toString()));
    }

    public record CreatePolicyRequest(
            String name,
            String description,
            String resourceType,
            String conditions,
            PolicyEffect effect
    ) {}
}
