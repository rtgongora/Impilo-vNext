package zw.gov.mohcc.impilo.rules.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;
import zw.gov.mohcc.impilo.rules.api.dto.*;
import zw.gov.mohcc.impilo.rules.service.RuleRegistryService;
import zw.gov.mohcc.impilo.rules.service.RuleService;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/rules")
public class RuleController {

    private final RuleService ruleService;
    private final RuleRegistryService registryService;

    public RuleController(RuleService ruleService, RuleRegistryService registryService) {
        this.ruleService = ruleService;
        this.registryService = registryService;
    }

    @PostMapping
    public ResponseEntity<RuleResponse> createRule(@Valid @RequestBody RuleRequest request) {
        FederationAuthority.requireNational();
        RequestContext ctx = RequestContextHolder.require();
        RuleResponse response = ruleService.createRule(request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RuleResponse>> listRules() {
        RequestContext ctx = RequestContextHolder.require();
        List<RuleResponse> rules = ruleService.listRules(ctx.tenantId());
        return ResponseEntity.ok(rules);
    }

    @PostMapping("/{key}/versions")
    public ResponseEntity<VersionResponse> createVersion(
            @PathVariable String key,
            @Valid @RequestBody CreateVersionRequest request) {
        FederationAuthority.requireNational();
        RequestContext ctx = RequestContextHolder.require();
        VersionResponse response = registryService.createVersion(key, request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{key}/activate")
    public ResponseEntity<ActivationResponse> activate(
            @PathVariable String key,
            @RequestParam("version") int version) {
        FederationAuthority.requireNational();
        RequestContext ctx = RequestContextHolder.require();
        ActivationResponse response = registryService.activate(key, version, ctx);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{key}/deactivate")
    public ResponseEntity<RuleResponse> deactivate(@PathVariable String key) {
        FederationAuthority.requireNational();
        RequestContext ctx = RequestContextHolder.require();
        RuleResponse response = registryService.deactivate(key, ctx);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{key}/evaluate")
    public ResponseEntity<EvaluateRuleResponse> evaluate(
            @PathVariable String key,
            @Valid @RequestBody EvaluateRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        EvaluateRuleResponse response = registryService.evaluate(key, request, ctx);
        return ResponseEntity.ok(response);
    }
}
