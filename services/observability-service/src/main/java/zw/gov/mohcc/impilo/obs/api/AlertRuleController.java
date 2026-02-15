package zw.gov.mohcc.impilo.obs.api;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.obs.core.AlertCondition;
import zw.gov.mohcc.impilo.obs.core.AlertRuleService;
import zw.gov.mohcc.impilo.obs.core.AlertSeverity;
import zw.gov.mohcc.impilo.obs.persistence.entity.AlertRuleEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

@RestController
@RequestMapping("/internal/v1/alert-rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    public AlertRuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AlertRuleEntity>> createAlertRule(
            @RequestBody CreateAlertRuleRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        AlertRuleEntity alertRule = alertRuleService.createAlertRule(
                ctx.tenantId(), ctx.actorId(), ctx.correlationId(),
                request.name(), request.description(),
                request.metricName(), request.condition(),
                request.threshold(), request.severity());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(alertRule, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AlertRuleEntity>>> listAlertRules() {
        TrustContext ctx = TrustContextHolder.require();

        List<AlertRuleEntity> alertRules = alertRuleService.listAlertRules(ctx.tenantId());

        return ResponseEntity.ok(
                ApiResponse.ok(alertRules, ctx.correlationId().toString()));
    }

    public record CreateAlertRuleRequest(
            String name,
            String description,
            String metricName,
            AlertCondition condition,
            BigDecimal threshold,
            AlertSeverity severity
    ) {}
}
