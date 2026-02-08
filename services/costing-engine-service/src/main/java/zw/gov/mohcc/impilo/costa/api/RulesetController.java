package zw.gov.mohcc.impilo.costa.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.domain.entity.ChargingRulesetEntity;
import zw.gov.mohcc.impilo.costa.service.RulesetService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

@RestController
@RequestMapping("/costa/v1/rulesets")
public class RulesetController {

    private final RulesetService rulesetService;

    public RulesetController(RulesetService rulesetService) {
        this.rulesetService = rulesetService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ChargingRulesetEntity>>> listRulesets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        var ctx = TrustContextHolder.require();
        Page<ChargingRulesetEntity> rulesets = rulesetService.listRulesets(ctx.tenantId(), PageRequest.of(page, size));
        PagedResponse<ChargingRulesetEntity> paged = PagedResponse.of(
                rulesets.getContent(), page, size, rulesets.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<ChargingRulesetEntity>> publish(@PathVariable Long id) {
        var ctx = TrustContextHolder.require();
        ChargingRulesetEntity ruleset = rulesetService.publish(id);
        return ResponseEntity.ok(ApiResponse.ok(ruleset, ctx.correlationId().toString()));
    }
}
