package zw.gov.mohcc.impilo.secharden.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.secharden.core.PackType;
import zw.gov.mohcc.impilo.secharden.core.PolicyPackService;
import zw.gov.mohcc.impilo.secharden.persistence.entity.PolicyPackEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

@RestController
@RequestMapping("/internal/v1/policy-packs")
public class PolicyPackController {

    private final PolicyPackService policyPackService;

    public PolicyPackController(PolicyPackService policyPackService) {
        this.policyPackService = policyPackService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PolicyPackEntity>> createPolicyPack(
            @RequestBody CreatePolicyPackRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        PolicyPackEntity pack = policyPackService.createPolicyPack(
                ctx.tenantId(), ctx.actorId(), ctx.correlationId(),
                request.name(), request.description(),
                request.packType(), request.rules());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(pack, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PolicyPackEntity>>> listPolicyPacks() {
        TrustContext ctx = TrustContextHolder.require();

        List<PolicyPackEntity> packs = policyPackService.listPolicyPacks(ctx.tenantId());

        return ResponseEntity.ok(
                ApiResponse.ok(packs, ctx.correlationId().toString()));
    }

    public record CreatePolicyPackRequest(
            String name,
            String description,
            PackType packType,
            String rules
    ) {}
}
