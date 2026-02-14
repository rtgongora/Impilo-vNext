package zw.gov.mohcc.impilo.rules.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;
import zw.gov.mohcc.impilo.rules.api.dto.RuleRequest;
import zw.gov.mohcc.impilo.rules.api.dto.RuleResponse;
import zw.gov.mohcc.impilo.rules.service.RuleService;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    public ResponseEntity<RuleResponse> createRule(@Valid @RequestBody RuleRequest request) {
        FederationAuthority.requireNational();
        RequestContext ctx = RequestContextHolder.require();
        RuleResponse response = ruleService.upsertRule(request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RuleResponse>> listRules() {
        RequestContext ctx = RequestContextHolder.require();
        List<RuleResponse> rules = ruleService.listRules(ctx.tenantId());
        return ResponseEntity.ok(rules);
    }
}
