package zw.gov.mohcc.impilo.datagovernance.api;

import zw.gov.mohcc.impilo.datagovernance.core.TenantKeys;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.datagovernance.api.dto.CreateRuleRequest;
import zw.gov.mohcc.impilo.datagovernance.api.dto.RuleResponse;
import zw.gov.mohcc.impilo.datagovernance.domain.GovernanceRuleEntity;
import zw.gov.mohcc.impilo.datagovernance.repository.GovernanceRuleRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
public class RulesController {

    private static final Logger log = LoggerFactory.getLogger(RulesController.class);
    private final GovernanceRuleRepository ruleRepository;

    public RulesController(GovernanceRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @PostMapping("/internal/v1/governance/rules")
    public ResponseEntity<?> createRule(@RequestBody CreateRuleRequest request,
                                         HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = TenantKeys.tenantUuid(ctx.tenantId());

        log.info("Create rule [name={}] correlationId={}", request.name(), ctx.correlationId());

        GovernanceRuleEntity rule = new GovernanceRuleEntity();
        rule.setTenantId(tenantId);
        rule.setName(request.name());
        rule.setDescription(request.description());
        rule.setResourcePattern(request.resourcePattern());
        rule.setAction(request.action() != null ? request.action() : "EXPORT");
        rule.setEffect(request.effect() != null ? request.effect() : "DENY");
        rule.setRequiredPurpose(request.requiredPurpose());
        rule.setPriority(request.priority() != null ? request.priority() : 100);
        rule.setActive(true);

        ruleRepository.save(rule);

        return ResponseEntity.status(HttpStatus.CREATED).body(toRuleResponse(rule));
    }

    @GetMapping("/internal/v1/governance/rules")
    public ResponseEntity<?> listRules() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = TenantKeys.tenantUuid(ctx.tenantId());

        List<RuleResponse> rules = ruleRepository.findByTenantIdAndActiveTrue(tenantId)
                .stream()
                .map(this::toRuleResponse)
                .toList();

        return ResponseEntity.ok(rules);
    }

    @GetMapping("/internal/v1/governance/rules/{ruleId}")
    public ResponseEntity<?> getRule(@PathVariable UUID ruleId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = TenantKeys.tenantUuid(ctx.tenantId());

        Optional<GovernanceRuleEntity> ruleOpt = ruleRepository.findByTenantIdAndRuleId(tenantId, ruleId);
        if (ruleOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("RULE_NOT_FOUND",
                            "Rule " + ruleId + " not found",
                            ctx.requestId(), ctx.correlationId()));
        }

        return ResponseEntity.ok(toRuleResponse(ruleOpt.get()));
    }

    @DeleteMapping("/internal/v1/governance/rules/{ruleId}")
    public ResponseEntity<?> deactivateRule(@PathVariable UUID ruleId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = TenantKeys.tenantUuid(ctx.tenantId());

        Optional<GovernanceRuleEntity> ruleOpt = ruleRepository.findByTenantIdAndRuleId(tenantId, ruleId);
        if (ruleOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("RULE_NOT_FOUND",
                            "Rule " + ruleId + " not found",
                            ctx.requestId(), ctx.correlationId()));
        }

        GovernanceRuleEntity rule = ruleOpt.get();
        rule.setActive(false);
        rule.setUpdatedAt(OffsetDateTime.now());
        ruleRepository.save(rule);

        return ResponseEntity.ok(toRuleResponse(rule));
    }

    private RuleResponse toRuleResponse(GovernanceRuleEntity e) {
        return new RuleResponse(e.getRuleId(), e.getTenantId(), e.getName(), e.getDescription(),
                e.getResourcePattern(), e.getAction(), e.getEffect(), e.getRequiredPurpose(),
                e.getPriority(), e.isActive(), e.getCreatedAt().toString());
    }
}
